package ru.muiv.fintracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.muiv.fintracker.config.BotProperties;
import ru.muiv.fintracker.model.AppUser;
import ru.muiv.fintracker.model.Transaction;
import ru.muiv.fintracker.model.TransactionType;
import ru.muiv.fintracker.repository.AppUserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Telegram-бот учёта финансов. Реализует интерфейс взаимодействия
 * с пользователем: приём сообщений, подтверждение транзакций,
 * исправление категории через inline-кнопки, вывод статистики.
 */
@Slf4j
@Component
public class FinTrackerBot extends TelegramLongPollingBot {

    private final BotProperties properties;
    private final TransactionParser parser;
    private final TransactionService transactionService;
    private final AppUserRepository userRepository;

    /** Полный перечень категорий (для клавиатуры исправления). */
    private static final List<String> CATEGORIES = List.of(
            "Продукты", "Кофейни", "Рестораны", "Транспорт", "Развлечения",
            "Здоровье", "Косметика", "Одежда", "Подарки", "Подписки",
            "Путешествия", "Спорт", "Учёба", "ЖКХ и аренда", "Связь",
            "Хозтовары", "Зарплата", "Подработка"
    );

    public FinTrackerBot(BotProperties properties,
                         TransactionParser parser,
                         TransactionService transactionService,
                         AppUserRepository userRepository) {
        super(properties.getToken());
        this.properties = properties;
        this.parser = parser;
        this.transactionService = transactionService;
        this.userRepository = userRepository;
    }

    @Override
    public String getBotUsername() {
        return properties.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleText(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки обновления", e);
        }
    }

    // ---------- обработка текстовых сообщений ----------

    private void handleText(Message message) throws TelegramApiException {
        Long chatId = message.getChatId();
        String text = message.getText().trim();

        switch (text) {
            case "/start" -> {
                registerUser(message);
                send(chatId, """
                        Привет! Я помогу вести учёт финансов. 💰

                        Просто напиши трату в свободной форме, например:
                        • такси 400
                        • пятёрочка молоко хлеб 850
                        • зарплата 60000

                        Я сам определю категорию с помощью нейросети.
                        Команды: /stats — статистика, /help — помощь.""");
            }
            case "/help" -> send(chatId, """
                    Как пользоваться:
                    1. Напиши трату: «кофе 250» или «250 кофе».
                    2. Я распознаю категорию и запишу операцию.
                    3. Если категория неверная — нажми «Исправить категорию».
                    4. /stats — сводка по категориям.""");
            case "/stats" -> sendStats(chatId);
            default -> handleTransaction(chatId, text);
        }
    }

    private void handleTransaction(Long chatId, String text) throws TelegramApiException {
        TransactionParser.ParsedInput parsed = parser.parse(text);

        if (parsed.amount() == null) {
            send(chatId, "Не нашёл сумму. Укажи её числом, например: «такси 400».");
            return;
        }
        if (parsed.description().isBlank()) {
            send(chatId, "Добавь описание к сумме, например: «кофе 250».");
            return;
        }

        Transaction t = transactionService.createTransaction(chatId, parsed.amount(), parsed.description());

        String typeLabel = t.getType() == TransactionType.INCOME ? "Доход" : "Расход";
        String confidenceNote = t.getConfidence() < 0.6
                ? "\n⚠️ Не уверен в категории — проверь." : "";

        String reply = String.format(
                "✅ Записал\n%s: %s\nСумма: %.0f ₽\nПримечание: %s%s",
                typeLabel, t.getCategory(), t.getAmount(), t.getDescription(), confidenceNote);

        SendMessage msg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(reply)
                .replyMarkup(fixButton(t.getId()))
                .build();
        execute(msg);
    }

    // ---------- обработка нажатий inline-кнопок ----------

    private void handleCallback(CallbackQuery callback) throws TelegramApiException {
        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();
        Integer messageId = callback.getMessage().getMessageId();

        if (data.startsWith("fix:")) {
            Long txId = Long.parseLong(data.substring(4));
            editMarkup(chatId, messageId, "Выбери правильную категорию:", categoryKeyboard(txId));

        } else if (data.startsWith("set:")) {
            String[] parts = data.split(":", 3);
            Long txId = Long.parseLong(parts[1]);
            String category = parts[2];
            transactionService.updateCategory(txId, category)
                    .ifPresent(t -> {});
            editText(chatId, messageId,
                    "✏️ Категория обновлена на: " + category);
        }
    }

    // ---------- статистика ----------

    private void sendStats(Long chatId) throws TelegramApiException {
        List<Transaction> history = transactionService.history(chatId);
        if (history.isEmpty()) {
            send(chatId, "Пока нет ни одной операции. Добавь первую: «кофе 250».");
            return;
        }

        double totalExpense = history.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .mapToDouble(Transaction::getAmount).sum();
        double totalIncome = history.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .mapToDouble(Transaction::getAmount).sum();

        Map<String, Double> byCategory = history.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(Transaction::getCategory,
                        Collectors.summingDouble(Transaction::getAmount)));

        StringBuilder sb = new StringBuilder("📊 Статистика\n\nРасходы по категориям:\n");
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> sb.append(String.format("• %s — %.0f ₽%n", e.getKey(), e.getValue())));

        sb.append(String.format("%nИтого расходы: %.0f ₽%n", totalExpense));
        sb.append(String.format("Итого доходы: %.0f ₽%n", totalIncome));
        sb.append(String.format("Баланс: %.0f ₽", totalIncome - totalExpense));

        send(chatId, sb.toString());
    }

    // ---------- вспомогательные методы ----------

    private void registerUser(Message message) {
        Long chatId = message.getChatId();
        if (!userRepository.existsById(chatId)) {
            userRepository.save(AppUser.builder()
                    .chatId(chatId)
                    .username(message.getFrom().getUserName())
                    .firstName(message.getFrom().getFirstName())
                    .build());
        }
    }

    private InlineKeyboardMarkup fixButton(Long txId) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("✏️ Исправить категорию")
                .callbackData("fix:" + txId)
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(button)))
                .build();
    }

    private InlineKeyboardMarkup categoryKeyboard(Long txId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (String category : CATEGORIES) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(category)
                    .callbackData("set:" + txId + ":" + category)
                    .build();
            row.add(button);
            if (row.size() == 2) {          // по 2 кнопки в ряд
                rows.add(new ArrayList<>(row));
                row.clear();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    private void send(Long chatId, String text) throws TelegramApiException {
        execute(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build());
    }

    private void editText(Long chatId, Integer messageId, String text) throws TelegramApiException {
        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text)
                .build();
        execute(edit);
    }

    private void editMarkup(Long chatId, Integer messageId, String text,
                            InlineKeyboardMarkup markup) throws TelegramApiException {
        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(text)
                .replyMarkup(markup)
                .build();
        execute(edit);
    }
}
