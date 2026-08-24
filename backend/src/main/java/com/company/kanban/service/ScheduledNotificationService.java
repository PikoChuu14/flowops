package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/** Runs all calendar-based reminders using one company-time-zone clock. */
@Service
public class ScheduledNotificationService {
    static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final String TASK = "TASK";
    private static final String MONTHLY_REPORT = "MONTHLY_REPORT";
    private final TaskRepository tasks;
    private final MonthlyWorkReportRepository reports;
    private final UserRepository users;
    private final NotificationDeliveryLogRepository deliveryLogs;
    private final NotificationService notifications;
    private final Clock clock;

    @Autowired
    public ScheduledNotificationService(TaskRepository tasks, MonthlyWorkReportRepository reports, UserRepository users,
                                        NotificationDeliveryLogRepository deliveryLogs, NotificationService notifications) {
        this(tasks, reports, users, deliveryLogs, notifications, Clock.system(ZONE));
    }
    ScheduledNotificationService(TaskRepository tasks, MonthlyWorkReportRepository reports, UserRepository users,
                                 NotificationDeliveryLogRepository deliveryLogs, NotificationService notifications, Clock clock) {
        this.tasks = tasks; this.reports = reports; this.users = users; this.deliveryLogs = deliveryLogs;
        this.notifications = notifications; this.clock = clock;
    }

    @Transactional
    public void runCheck() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        checkTasks(today);
        checkMonthlyReports(today);
    }

    private void checkTasks(LocalDate today) {
        for (Task task : tasks.findAll()) {
            if (task.getStatus() == TaskStatus.DONE || task.getDueDate() == null || task.getAssignee() == null
                    || task.getAssignee().getStatus() != AccountStatus.ACTIVE) continue;
            LocalDate due = task.getDueDate();
            if (due.equals(today.plusDays(1))) {
                sendTask(task, NotificationType.TASK_DUE_TOMORROW, today, "Task due tomorrow", task.getTitle() + " is due tomorrow.");
            } else if (due.equals(today)) {
                sendTask(task, NotificationType.TASK_DUE_TODAY, today, "Task due today", task.getTitle() + " is due today.");
            } else if (due.isBefore(today)) {
                long days = due.until(today, java.time.temporal.ChronoUnit.DAYS);
                sendTask(task, NotificationType.TASK_OVERDUE, today, "Task overdue",
                        task.getTitle() + " is overdue by " + days + " day" + (days == 1 ? "" : "s") + ".");
            }
        }
    }

    private void sendTask(Task task, NotificationType kind, LocalDate date, String title, String message) {
        User recipient = task.getAssignee();
        if (deliveryLogs.existsByRecipientUserIdAndEntityTypeAndEntityIdAndNotificationKindAndNotificationDate(
                recipient.getId(), TASK, task.getId(), kind, date)) return;
        deliveryLogs.save(new NotificationDeliveryLog(recipient.getId(), TASK, task.getId(), kind, date));
        Long boardId = task.isGeneralTask() ? null : task.getColumn().getBoard().getId();
        notifications.notifyScheduled(recipient, kind, title, message, task.getId(), boardId,
                NotificationService.destination(kind, task.getId(), boardId, null, null));
    }

    private void checkMonthlyReports(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        LocalDate monthEnd = current.atEndOfMonth();
        if (today.equals(monthEnd.minusDays(3))) remindUnsubmitted(current, today, NotificationType.MONTHLY_REPORT_3_DAY_REMINDER,
                "Monthly report due soon", "Your monthly report for " + current + " is due in 3 days.");
        if (today.equals(monthEnd)) remindUnsubmitted(current, today, NotificationType.MONTHLY_REPORT_MONTH_END,
                "Monthly report due today", "Your monthly report for " + current + " is due today.");
        YearMonth previous = current.minusMonths(1);
        remindUnsubmitted(previous, today, NotificationType.MONTHLY_REPORT_OVERDUE,
                "Monthly report overdue", "Your monthly report for " + previous + " is overdue.");
    }

    private void remindUnsubmitted(YearMonth period, LocalDate date, NotificationType kind, String title, String message) {
        for (User user : users.findByStatus(AccountStatus.ACTIVE)) {
            if (user.getRole() != Role.STAFF) continue;
            if (reports.findByUserIdAndYearAndMonth(user.getId(), period.getYear(), period.getMonthValue())
                    .map(r -> r.getStatus() == MonthlyWorkReportStatus.SUBMITTED).orElse(false)) continue;
            Long periodKey = (long) period.getYear() * 100 + period.getMonthValue();
            if (deliveryLogs.existsByRecipientUserIdAndEntityTypeAndEntityIdAndNotificationKindAndNotificationDate(
                    user.getId(), MONTHLY_REPORT, periodKey, kind, date)) continue;
            deliveryLogs.save(new NotificationDeliveryLog(user.getId(), MONTHLY_REPORT, periodKey, kind, date));
            notifications.notifyScheduled(user, kind, title, message, null, null,
                    "/reports/monthly?year=" + period.getYear() + "&month=" + period.getMonthValue());
        }
    }
}
