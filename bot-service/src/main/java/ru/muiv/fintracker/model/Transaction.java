package ru.muiv.fintracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Финансовая операция (транзакция) пользователя.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;                 // владелец транзакции

    private Double amount;               // сумма

    @Column(length = 512)
    private String description;          // текстовое описание (ввод пользователя)

    private String category;             // категория, определённая нейросетью

    @Enumerated(EnumType.STRING)
    private TransactionType type;        // доход / расход

    private Double confidence;           // уверенность модели (0..1)

    private LocalDateTime createdAt;     // дата и время добавления
}
