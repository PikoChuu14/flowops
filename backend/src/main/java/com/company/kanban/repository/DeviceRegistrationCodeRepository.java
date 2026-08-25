package com.company.kanban.repository;

import com.company.kanban.entity.DeviceRegistrationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.time.LocalDateTime;

public interface DeviceRegistrationCodeRepository extends JpaRepository<DeviceRegistrationCode, Long> {
    Optional<DeviceRegistrationCode> findByCodeHash(String codeHash);
    long deleteByExpiresAtBeforeOrUsedAtBefore(LocalDateTime expiresAt, LocalDateTime usedAt);
}
