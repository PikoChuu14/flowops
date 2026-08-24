package com.company.kanban.service;

import com.company.kanban.dto.AgentNotificationResponse;
import com.company.kanban.entity.Notification;
import com.company.kanban.entity.User;
import com.company.kanban.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AgentNotificationService {
    private final NotificationRepository notifications;
    public AgentNotificationService(NotificationRepository notifications) { this.notifications = notifications; }

    @Transactional(readOnly = true)
    public List<AgentNotificationResponse> after(User user, long cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        return notifications.findByRecipientIdAndIdGreaterThanOrderByIdAsc(user.getId(), Math.max(0, cursor), PageRequest.of(0, limit))
                .stream().map(this::response).toList();
    }

    private AgentNotificationResponse response(Notification n) {
        String destination = n.getDestination() == null
                ? NotificationService.destination(n.getType(), n.getTaskId(), n.getBoardId(), n.getDailyReportId(), n.getRawMaterialArrivalId())
                : n.getDestination();
        return new AgentNotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getCreatedAt(), destination);
    }
}
