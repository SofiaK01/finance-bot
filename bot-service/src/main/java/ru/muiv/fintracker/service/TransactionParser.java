package ru.muiv.fintracker.service;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор пользовательского сообщения на сумму и описание.
 * Поддерживает форматы: «такси 400», «400 такси», «кофе с собой 250».
 */
@Service
public class TransactionParser {

    // число: целое или с десятичной частью (точка или запятая)
    private static final Pattern AMOUNT = Pattern.compile("(\\d+[.,]?\\d*)");

    /**
     * Результат разбора сообщения.
     */
    public record ParsedInput(Double amount, String description) {
    }

    /**
     * Пытается извлечь сумму и описание. Если суммы нет — amount = null.
     */
    public ParsedInput parse(String message) {
        if (message == null || message.isBlank()) {
            return new ParsedInput(null, "");
        }
        String text = message.trim();
        Matcher matcher = AMOUNT.matcher(text);

        Double amount = null;
        if (matcher.find()) {
            String raw = matcher.group(1).replace(",", ".");
            try {
                amount = Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
            }
            // удаляем найденную сумму из текста, остальное — описание
            text = text.substring(0, matcher.start()) + text.substring(matcher.end());
        }
        String description = text.replaceAll("\\s+", " ").trim();
        return new ParsedInput(amount, description);
    }
}
