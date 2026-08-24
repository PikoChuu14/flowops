package com.company.kanban.service;

import com.company.kanban.dto.NotificationResponse;
import com.company.kanban.entity.*;
import com.company.kanban.repository.NotificationRepository;
import com.company.kanban.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(User currentUser, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return notificationRepository
                .findByRecipientIdAndClearedAtIsNullOrderByCreatedAtDesc(currentUser.getId(), PageRequest.of(0, limit))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(User currentUser) {
        return Map.of("count", notificationRepository.countByRecipientIdAndReadFalseAndClearedAtIsNull(currentUser.getId()));
    }

    @Transactional
    public NotificationResponse markRead(Long id, User currentUser) {
        Notification notification = owned(id, currentUser);
        notification.markRead();
        // Flush before returning so a subsequent panel refresh can never read
        // the old value from a still-pending persistence context.
        notificationRepository.saveAndFlush(notification);
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead(User currentUser) {
        List<Notification> unread = notificationRepository
                .findByRecipientIdAndReadFalseAndClearedAtIsNull(currentUser.getId());
        unread.forEach(Notification::markRead);
        notificationRepository.saveAllAndFlush(unread);
    }

    @Transactional
    public void clear(Long id, User currentUser) {
        Notification notification = owned(id, currentUser);
        notification.clear();
        notificationRepository.saveAndFlush(notification);
    }

    @Transactional
    public void clearAll(User currentUser) {
        List<Notification> visible = notificationRepository
                .findByRecipientIdAndClearedAtIsNull(currentUser.getId());
        visible.forEach(Notification::clear);
        notificationRepository.saveAllAndFlush(visible);
    }

    public void notifyTaskAssigned(Task task, User actor) {
        User assignee = task.getAssignee();
        if (assignee != null) notifyUser(assignee, actor, NotificationType.TASK_ASSIGNED,
                "New task assigned", actor.getName() + " assigned \"" + task.getTitle() + "\" to you.", task, null);
    }

    public void notifyTaskUpdated(Task task, User actor, String changeSummary) {
        String message = changeSummary + " for \"" + task.getTitle() + "\" was changed by " + actor.getName() + ".";
        notifyUser(task.getAssignee(), actor, NotificationType.TASK_UPDATED, "Task updated", message, task, null);
        if (task.getCreatedBy() != null && (task.getAssignee() == null || !task.getCreatedBy().getId().equals(task.getAssignee().getId())))
            notifyUser(task.getCreatedBy(), actor, NotificationType.TASK_UPDATED, "Task updated", message, task, null);
    }

    public void notifyTaskReassigned(Task task, User oldAssignee, User newAssignee, User actor) {
        if (oldAssignee != null && !oldAssignee.getId().equals(newAssignee.getId()))
            notifyUser(oldAssignee, actor, NotificationType.TASK_REASSIGNED, "Task reassigned",
                    "\"" + task.getTitle() + "\" was reassigned from you to " + newAssignee.getName() + " by " + actor.getName() + ".", task, null);
        notifyUser(newAssignee, actor, NotificationType.TASK_ASSIGNED, "Task assigned",
                actor.getName() + " assigned \"" + task.getTitle() + "\" to you.", task, null);
    }

    public void notifyReviewSubmitted(Task task, User actor) {
        userRepository.findByDepartmentIdAndRole(task.getDepartment().getId(), Role.MANAGER)
                .forEach(manager -> notifyUser(manager, actor, NotificationType.TASK_REVIEW_SUBMITTED, "Review requested",
                        actor.getName() + " submitted \"" + task.getTitle() + "\" for review.", task, null));
    }

    public void notifyReviewResult(Task task, User actor, boolean approved) {
        notifyUser(task.getAssignee(), actor,
                approved ? NotificationType.TASK_APPROVED : NotificationType.TASK_REVIEW_RETURNED,
                approved ? "Task approved" : "Task returned",
                actor.getName() + (approved ? " approved \"" : " returned \"") + task.getTitle()
                        + (approved ? "\"." : "\" to Doing."), task, null);
    }

    public void notifyProjectCreated(Board board, User actor) {
        userRepository.findByDepartmentIdOrderByNameAsc(board.getDepartment().getId()).forEach(user ->
                notifyUser(user, actor, NotificationType.PROJECT_CREATED, "New project",
                        "\"" + board.getName() + "\" was created in " + board.getDepartment().getName() + " by " + actor.getName() + ".",
                        null, board.getId()));
    }

    public void notifyDailyReportSubmitted(DailyWorkReport report, User actor) {
        if (actor.getRole() != Role.STAFF) return;
        String date = report.getReportDate().format(DateTimeFormatter.ofPattern("d MMM uuuu"));
        userRepository.findByDepartmentIdAndRole(actor.getDepartment().getId(), Role.MANAGER).forEach(manager ->
                notifyUser(manager, actor, NotificationType.DAILY_REPORT_SUBMITTED, "Daily report submitted",
                        actor.getName() + " submitted their Daily Report for " + date + ".", null, null, report.getId()));
    }

    private void notifyUser(User recipient, User actor, NotificationType type, String title, String message, Task task, Long boardId) {
        notifyUser(recipient, actor, type, title, message, task, boardId, null);
    }

    private void notifyUser(User recipient, User actor, NotificationType type, String title, String message,
                            Task task, Long boardId, Long dailyReportId) {
        if (recipient == null || actor == null || recipient.getId().equals(actor.getId())) return;
        notificationRepository.save(new Notification(recipient, type, title, message,
                task == null ? null : task.getId(),
                task == null ? boardId : task.isGeneralTask() ? null : task.getColumn().getBoard().getId(), dailyReportId));
    }

    private Notification owned(Long id, User currentUser) {
        return notificationRepository.findByIdAndRecipientIdAndClearedAtIsNull(id, currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.isRead(),
                n.getCreatedAt(), n.getTaskId(), n.getBoardId(), n.getDailyReportId(), n.getRawMaterialArrivalId());
    }
}
