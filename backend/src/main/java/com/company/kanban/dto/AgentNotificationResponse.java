package com.company.kanban.dto;

import com.company.kanban.entity.NotificationType;
import java.time.LocalDateTime;

public record AgentNotificationResponse(Long id, NotificationType type, String title, String message,
                                        LocalDateTime createdAt, String destination) {}
