package ru.muiv.fintracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.muiv.fintracker.model.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
}
