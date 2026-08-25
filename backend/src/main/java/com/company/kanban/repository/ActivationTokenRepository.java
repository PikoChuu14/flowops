package com.company.kanban.repository;

import com.company.kanban.entity.ActivationToken;
import com.company.kanban.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {
    Optional<ActivationToken> findByTokenHash(String tokenHash);
    void deleteByUser(User user);
    long deleteByExpiresAtBeforeOrConsumedAtBefore(LocalDateTime expiresAt, LocalDateTime consumedAt);
}
