package com.company.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class DeviceDtos {
    private DeviceDtos() {}

    public record RegistrationCodeResponse(String code, LocalDateTime expiresAt) {}
    public record ExchangeRequest(@NotBlank String code, @NotBlank @Size(max = 160) String deviceName) {}
    public record ExchangeResponse(String deviceToken, long notificationCursor, Long userId, String userName) {}
    public record DeviceResponse(Long id, String deviceName, LocalDateTime createdAt, LocalDateTime lastUsedAt,
                                 LocalDateTime revokedAt, boolean active) {}
}
