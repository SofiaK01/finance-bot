package ru.muiv.fintracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.muiv.fintracker.model.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByChatIdOrderByCreatedAtDesc(Long chatId);

    Optional<Transaction> findTopByChatIdOrderByCreatedAtDesc(Long chatId);
}
