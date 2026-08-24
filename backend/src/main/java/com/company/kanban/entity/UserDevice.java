package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices", indexes = {
        @Index(name = "idx_user_device_user", columnList = "user_id"),
        @Index(name = "idx_user_device_token_hash", columnList = "device_token_hash", unique = true)
})
public class UserDevice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 160)
    private String deviceName;

    @Column(nullable = false, unique = true, length = 24)
    private String tokenIdentifier;

    @Column(name = "device_token_hash", nullable = false, unique = true, length = 64)
    private String deviceTokenHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;

    protected UserDevice() {}

    public UserDevice(User user, String deviceName, String tokenIdentifier, String deviceTokenHash, LocalDateTime createdAt) {
        this.user = user;
        this.deviceName = deviceName;
        this.tokenIdentifier = tokenIdentifier;
        this.deviceTokenHash = deviceTokenHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getDeviceName() { return deviceName; }
    public String getTokenIdentifier() { return tokenIdentifier; }
    public String getDeviceTokenHash() { return deviceTokenHash; }
    public void setDeviceTokenHash(String deviceTokenHash) { this.deviceTokenHash = deviceTokenHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public boolean isActive() { return revokedAt == null; }
    public void touch(LocalDateTime now) { lastUsedAt = now; }
    public void revoke(LocalDateTime now) { if (revokedAt == null) revokedAt = now; }
}
