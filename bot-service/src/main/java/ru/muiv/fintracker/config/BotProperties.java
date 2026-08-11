package ru.muiv.fintracker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки бота из application.yml (секция bot.*).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bot")
public class BotProperties {
    /** Имя бота, выданное BotFather (без @). */
    private String username;
    /** Токен бота от BotFather. */
    private String token;
    /** URL сервиса нейросети-классификатора. */
    private String mlServiceUrl;
}
