package ru.muiv.fintracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Общие бины приложения.
 */
@Configuration
public class AppConfig {

    /** HTTP-клиент для обращения к сервису нейросети. */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
