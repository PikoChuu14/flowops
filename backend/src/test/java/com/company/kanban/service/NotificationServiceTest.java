package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.NotificationRepository;
import com.company.kanban.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService service = new NotificationService(notifications, users);

    @Test
    void assignedTaskNotifiesAssigneeButNeverActor() {
        Department department = entity(new Department("RDD"), 1L);
        User actor = entity(new User("Hana", "hana@test", "x", Role.MANAGER, department), 2L);
        User assignee = entity(new User("Amir", "amir@test", "x", Role.STAFF, department), 3L);
        Board board = entity(new Board("Prototype", "", department), 4L);
        KanbanColumn column = entity(new KanbanColumn("To Do", 1, board), 5L);
        Task task = new Task("Prepare BOM", "", Priority.MEDIUM, null, 1, column, assignee);
        ReflectionTestUtils.setField(task, "id", 6L);

        service.notifyTaskAssigned(task, actor);
        verify(notifications).save(argThat(notification ->
                notification.getRecipient() == assignee
                        && notification.getType() == NotificationType.TASK_ASSIGNED
                        && notification.getTaskId().equals(6L)
                        && notification.getBoardId().equals(4L)));

        reset(notifications);
        task.setAssignee(actor);
        service.notifyTaskAssigned(task, actor);
        verifyNoInteractions(notifications);
    }

    @Test
    void anotherUsersNotificationCannotBeModified() {
        Department department = entity(new Department("RDD"), 1L);
        User amir = entity(new User("Amir", "amir@test", "x", Role.STAFF, department), 3L);
        when(notifications.findByIdAndRecipientIdAndClearedAtIsNull(99L, 3L)).thenReturn(Optional.empty());

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.markRead(99L, amir));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void markReadIsFlushedBeforeTheUpdatedResponseReturns() {
        Department department = entity(new Department("RDD"), 1L);
        User amir = entity(new User("Amir", "amir@test", "x", Role.STAFF, department), 3L);
        Notification notification = new Notification(
                amir, NotificationType.TASK_ASSIGNED, "New task", "Assigned to you", 10L, 20L, null);
        ReflectionTestUtils.setField(notification, "id", 99L);
        when(notifications.findByIdAndRecipientIdAndClearedAtIsNull(99L, 3L))
                .thenReturn(Optional.of(notification));

        var response = service.markRead(99L, amir);

        assertTrue(response.read());
        verify(notifications).saveAndFlush(notification);
    }

    @Test
    void generalTaskNotificationKeepsTaskLinkWithoutFakeBoard() {
        Department department = entity(new Department("PPC"), 1L);
        User actor = entity(new User("Manager", "manager@test", "x", Role.MANAGER, department), 2L);
        User assignee = entity(new User("Staff", "staff@test", "x", Role.STAFF, department), 3L);
        Task task = new Task("Supplier follow-up", "", Priority.MEDIUM, null, 1, null, assignee);
        task.setDepartment(department);
        ReflectionTestUtils.setField(task, "id", 6L);

        service.notifyTaskAssigned(task, actor);

        verify(notifications).save(argThat(notification -> notification.getTaskId().equals(6L)
                && notification.getBoardId() == null));
    }

    private static <T> T entity(T value, Long id) {
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
