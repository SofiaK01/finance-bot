package ru.muiv.fintracker.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Пользователь бота. Идентифицируется по chatId Telegram.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    private Long chatId;          // идентификатор чата Telegram

    private String username;      // @username
    private String firstName;     // имя из профиля Telegram
}
