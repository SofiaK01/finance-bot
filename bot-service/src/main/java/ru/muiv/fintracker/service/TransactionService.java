package ru.muiv.fintracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.muiv.fintracker.model.Transaction;
import ru.muiv.fintracker.model.TransactionType;
import ru.muiv.fintracker.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Бизнес-логика работы с транзакциями: сохранение, статистика,
 * исправление категории.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;
    private final CategoryClient categoryClient;

    // категории, которые относятся к доходам
    private static final Set<String> INCOME_CATEGORIES = Set.of("Зарплата", "Подработка");

    /**
     * Создаёт транзакцию: определяет категорию нейросетью и сохраняет.
     */
    public Transaction createTransaction(Long chatId, Double amount, String description) {
        CategoryClient.CategoryResult result = categoryClient.recognize(description);

        Transaction transaction = Transaction.builder()
                .chatId(chatId)
                .amount(amount)
                .description(description)
                .category(result.category())
                .confidence(result.confidence())
                .type(resolveType(result.category()))
                .createdAt(LocalDateTime.now())
                .build();

        return repository.save(transaction);
    }

    /**
     * Обновляет категорию последней (или конкретной) транзакции —
     * используется при исправлении пользователем.
     */
    public Optional<Transaction> updateCategory(Long transactionId, String newCategory) {
        return repository.findById(transactionId).map(t -> {
            t.setCategory(newCategory);
            t.setType(resolveType(newCategory));
            return repository.save(t);
        });
    }

    public List<Transaction> history(Long chatId) {
        return repository.findByChatIdOrderByCreatedAtDesc(chatId);
    }

    private TransactionType resolveType(String category) {
        return INCOME_CATEGORIES.contains(category) ? TransactionType.INCOME : TransactionType.EXPENSE;
    }
}
