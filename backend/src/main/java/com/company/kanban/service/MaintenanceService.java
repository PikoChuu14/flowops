package com.company.kanban.service;

import com.company.kanban.repository.ActivationTokenRepository;
import com.company.kanban.repository.DeviceRegistrationCodeRepository;
import com.company.kanban.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/** Scheduled cleanup for disposable operational data only. Business records are never touched here. */
@Service
public class MaintenanceService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private final NotificationRepository notifications;
    private final ActivationTokenRepository activationTokens;
    private final DeviceRegistrationCodeRepository registrationCodes;
    private final DataManagementService dataManagement;
    private final int notificationDays;

    public MaintenanceService(NotificationRepository notifications, ActivationTokenRepository activationTokens,
                              DeviceRegistrationCodeRepository registrationCodes, DataManagementService dataManagement,
                              @Value("${flowops.retention.notification-days:90}") int notificationDays) {
        this.notifications = notifications; this.activationTokens = activationTokens;
        this.registrationCodes = registrationCodes; this.dataManagement = dataManagement; this.notificationDays = notificationDays;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kuala_Lumpur")
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        notifications.deleteOlderThan(now.minusDays(notificationDays));
        activationTokens.deleteByExpiresAtBeforeOrConsumedAtBefore(now, now);
        registrationCodes.deleteByExpiresAtBeforeOrUsedAtBefore(now, now);
        dataManagement.rotateBackups(now);
    }
}
