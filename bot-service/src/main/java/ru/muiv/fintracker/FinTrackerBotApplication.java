package ru.muiv.fintracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа приложения Telegram-бота учёта финансов.
 */
@SpringBootApplication
public class FinTrackerBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinTrackerBotApplication.class, args);
    }
}
