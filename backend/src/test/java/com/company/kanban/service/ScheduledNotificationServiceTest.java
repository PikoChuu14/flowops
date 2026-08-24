package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduledNotificationServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final MonthlyWorkReportRepository reports = mock(MonthlyWorkReportRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationDeliveryLogRepository logs = mock(NotificationDeliveryLogRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Department department = entity(new Department("RDD"), 1L);
    private final User bob = entity(new User("Bob", "bob@test", "x", Role.STAFF, department), 2L);

    @Test
    void dueTomorrowIsSentOnceAndUsesCorrectType() {
        Task task = task(LocalDate.of(2026, 8, 25));
        when(tasks.findAll()).thenReturn(List.of(task));
        when(users.findByStatus(AccountStatus.ACTIVE)).thenReturn(List.of());
        when(logs.existsByRecipientUserIdAndEntityTypeAndEntityIdAndNotificationKindAndNotificationDate(anyLong(), anyString(), anyLong(), any(), any())).thenReturn(false);

        service("2026-08-24").runCheck();

        verify(notifications).notifyScheduled(eq(bob), eq(NotificationType.TASK_DUE_TOMORROW), eq("Task due tomorrow"), anyString(), eq(10L), isNull(), anyString());
        verify(logs).save(any(NotificationDeliveryLog.class));
    }

    @Test
    void duplicateDueTomorrowIsBlockedPersistently() {
        when(tasks.findAll()).thenReturn(List.of(task(LocalDate.of(2026, 8, 25))));
        when(users.findByStatus(AccountStatus.ACTIVE)).thenReturn(List.of());
        when(logs.existsByRecipientUserIdAndEntityTypeAndEntityIdAndNotificationKindAndNotificationDate(anyLong(), anyString(), anyLong(), any(), any())).thenReturn(true);

        service("2026-08-24").runCheck();

        verify(notifications, never()).notifyScheduled(any(), any(), anyString(), anyString(), any(), any(), anyString());
        verify(logs, never()).save(any());
    }

    @Test
    void overdueMessageUsesCalendarDayDifference() {
        when(tasks.findAll()).thenReturn(List.of(task(LocalDate.of(2026, 8, 22))));
        when(users.findByStatus(AccountStatus.ACTIVE)).thenReturn(List.of());
        when(logs.existsByRecipientUserIdAndEntityTypeAndEntityIdAndNotificationKindAndNotificationDate(anyLong(), anyString(), anyLong(), any(), any())).thenReturn(false);

        service("2026-08-24").runCheck();

        verify(notifications).notifyScheduled(eq(bob), eq(NotificationType.TASK_OVERDUE), eq("Task overdue"), contains("overdue by 2 days"), eq(10L), isNull(), anyString());
    }

    @Test
    void doneTaskAndDisabledAssigneeAreExcluded() {
        Task done = task(LocalDate.of(2026, 8, 22)); done.setStatus(TaskStatus.DONE);
        User disabled = entity(new User("Disabled", "disabled@test", "x", Role.STAFF, department), 3L); disabled.setStatus(AccountStatus.DISABLED);
        Task disabledTask = task(LocalDate.of(2026, 8, 22)); disabledTask.setAssignee(disabled); ReflectionTestUtils.setField(disabledTask, "id", 11L);
        when(tasks.findAll()).thenReturn(List.of(done, disabledTask));
        when(users.findByStatus(AccountStatus.ACTIVE)).thenReturn(List.of());
        service("2026-08-24").runCheck();
        verifyNoInteractions(notifications);
    }

    @Test
    void monthlyThreeDayReminderTargetsActiveStaffOnly() {
        when(tasks.findAll()).thenReturn(List.of());
        when(users.findByStatus(AccountStatus.ACTIVE)).thenReturn(List.of(bob));
        when(reports.findByUserIdAndYearAndMonth(2L, 2026, 8)).thenReturn(Optional.empty());
        when(logs.existsByRecipientUserIdAndEntityTypeAndEntityIdAndNotificationKindAndNotificationDate(anyLong(), anyString(), anyLong(), any(), any())).thenReturn(false);
        service("2026-08-28").runCheck();
        verify(notifications).notifyScheduled(eq(bob), eq(NotificationType.MONTHLY_REPORT_3_DAY_REMINDER), eq("Monthly report due soon"), anyString(), isNull(), isNull(), contains("year=2026&month=8"));
    }

    @Test
    void submittedPreviousMonthReportStopsOverdueReminder() {
        MonthlyWorkReport report = new MonthlyWorkReport(bob, 2026, 8); report.submit();
        when(tasks.findAll()).thenReturn(List.of());
        when(users.findByStatus(AccountStatus.ACTIVE)).thenReturn(List.of(bob));
        when(reports.findByUserIdAndYearAndMonth(2L, 2026, 8)).thenReturn(Optional.of(report));
        service("2026-09-01").runCheck();
        verifyNoInteractions(notifications);
    }

    private ScheduledNotificationService service(String date) {
        return new ScheduledNotificationService(tasks, reports, users, logs, notifications,
                Clock.fixed(LocalDate.parse(date).atStartOfDay(ScheduledNotificationService.ZONE).toInstant(), ScheduledNotificationService.ZONE));
    }
    private Task task(LocalDate due) {
        Task task = new Task("Prepare report", "", Priority.MEDIUM, due, 1, null, bob);
        task.setDepartment(department); return entity(task, 10L);
    }
    private static <T> T entity(T value, Long id) { ReflectionTestUtils.setField(value, "id", id); return value; }
}
