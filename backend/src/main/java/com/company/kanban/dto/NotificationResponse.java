package com.company.kanban.dto;

import com.company.kanban.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt,
        Long taskId,
        Long boardId,
        Long dailyReportId,
        Long rawMaterialArrivalId
) {}
