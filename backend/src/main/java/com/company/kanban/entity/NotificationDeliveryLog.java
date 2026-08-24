package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Persistent idempotency record for calendar-based notification checks. */
@Entity
@Table(name = "notification_delivery_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_delivery",
                columnNames = {"recipient_user_id", "entity_type", "entity_id", "notification_kind", "notification_date"}))
public class NotificationDeliveryLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recipient_user_id", nullable = false) private Long recipientUserId;
    @Column(name = "entity_type", nullable = false, length = 40) private String entityType;
    @Column(name = "entity_id", nullable = false) private Long entityId;
    @Enumerated(EnumType.STRING) @Column(name = "notification_kind", nullable = false, length = 50)
    private NotificationType notificationKind;
    @Column(name = "notification_date", nullable = false) private LocalDate notificationDate;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected NotificationDeliveryLog() {}
    public NotificationDeliveryLog(Long recipientUserId, String entityType, Long entityId,
                                   NotificationType notificationKind, LocalDate notificationDate) {
        this.recipientUserId = recipientUserId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.notificationKind = notificationKind;
        this.notificationDate = notificationDate;
        this.createdAt = LocalDateTime.now();
    }
}
