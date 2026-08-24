package com.company.kanban.repository;

import com.company.kanban.entity.NotificationDeliveryLog;
import com.company.kanban.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLog, Long> {
    boolean existsByRecipientUserIdAndEntityTypeAndEntityIdAndNotificationKindAndNotificationDate(
            Long recipientUserId, String entityType, Long entityId, NotificationType notificationKind, LocalDate notificationDate);
}
