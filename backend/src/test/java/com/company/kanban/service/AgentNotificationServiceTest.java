package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentNotificationServiceTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final AgentNotificationService service = new AgentNotificationService(notifications);

    @Test void endpointQueryUsesAuthenticatedUsersIdAndCursor() {
        User bob = user(7L); Notification task = notification(bob, 12L, NotificationType.TASK_ASSIGNED, 44L, 9L, null);
        when(notifications.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(7L), eq(10L), any(Pageable.class))).thenReturn(List.of(task));
        var result = service.after(bob, 10, 100);
        assertEquals(List.of(12L), result.stream().map(item -> item.id()).toList());
        verify(notifications).findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(7L), eq(10L), any(Pageable.class));
    }

    @Test void taskNotificationIncludesGenericClickDestination() {
        User bob = user(7L); Notification task = notification(bob, 12L, NotificationType.TASK_ASSIGNED, 44L, 9L, null);
        when(notifications.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(7L), eq(0L), any(Pageable.class))).thenReturn(List.of(task));
        assertEquals("/projects?boardId=9&taskId=44", service.after(bob, 0, 100).getFirst().destination());
    }

    @Test void rawMaterialNotificationWorksThroughSameAgentEndpoint() {
        User bob = user(7L); Notification raw = notification(bob, 13L, NotificationType.RAW_MATERIAL_DELAYED, null, null, 88L);
        when(notifications.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(7L), eq(12L), any(Pageable.class))).thenReturn(List.of(raw));
        var response = service.after(bob, 12, 100).getFirst();
        assertEquals(NotificationType.RAW_MATERIAL_DELAYED, response.type());
        assertEquals("/ppc/raw-material-arrivals?arrivalId=88", response.destination());
    }

    private static User user(Long id) {
        Department d = new Department("PPC"); ReflectionTestUtils.setField(d, "id", 1L);
        User u = new User("Bob", "bob@test", "x", Role.STAFF, d); ReflectionTestUtils.setField(u, "id", id); return u;
    }
    private static Notification notification(User user, Long id, NotificationType type, Long taskId, Long boardId, Long arrivalId) {
        Notification n = new Notification(user, type, "Title", "Message", taskId, boardId, null, arrivalId);
        ReflectionTestUtils.setField(n, "id", id); return n;
    }
}
