package ru.muiv.fintracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.muiv.fintracker.config.BotProperties;

import java.util.Map;

/**
 * Клиент к сервису нейросети. Отправляет описание транзакции и
 * получает предсказанную категорию с уверенностью модели.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryClient {

    private final RestTemplate restTemplate;
    private final BotProperties properties;

    /**
     * Результат распознавания категории.
     */
    public record CategoryResult(String category, double confidence) {
    }

    /**
     * Запрашивает категорию у нейросети. При недоступности сервиса
     * возвращает резервную категорию «Нераспознанное».
     */
    public CategoryResult recognize(String text) {
        try {
            String url = properties.getMlServiceUrl() + "/predict";
            Map<String, String> body = Map.of("text", text);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

            if (response != null && response.get("category") != null) {
                String category = String.valueOf(response.get("category"));
                double confidence = Double.parseDouble(String.valueOf(response.get("confidence")));
                return new CategoryResult(category, confidence);
            }
        } catch (Exception e) {
            log.warn("Сервис распознавания недоступен: {}", e.getMessage());
        }
        return new CategoryResult("Нераспознанное", 0.0);
    }
}
