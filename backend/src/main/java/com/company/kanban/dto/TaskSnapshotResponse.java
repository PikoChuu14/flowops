package com.company.kanban.dto;

import com.company.kanban.entity.Priority;
import com.company.kanban.entity.SnapshotType;
import com.company.kanban.entity.TaskSnapshot;
import com.company.kanban.entity.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskSnapshotResponse(
        Long taskId, String title, String description, TaskStatus status,
        Integer workload, Priority priority, LocalDate dueDate, Integer position,
        Long assigneeId, String assigneeName, Long createdById, String createdByName,
        Long boardId, String boardName, Long departmentId, String departmentName,
        String columnName, SnapshotType snapshotType, LocalDate snapshotDate,
        LocalDateTime capturedAt, boolean recovered) {
    public static TaskSnapshotResponse from(TaskSnapshot snapshot) {
        var batch = snapshot.getBatch();
        return new TaskSnapshotResponse(snapshot.getTaskId(), snapshot.getTitle(), snapshot.getDescription(),
                snapshot.getStatus(), snapshot.getWorkload(), snapshot.getPriority(), snapshot.getDueDate(),
                snapshot.getPosition(), snapshot.getAssigneeId(), snapshot.getAssigneeName(),
                snapshot.getCreatedById(), snapshot.getCreatedByName(), snapshot.getBoardId(),
                snapshot.getBoardName() == null ? "General Task" : snapshot.getBoardName(),
                snapshot.getDepartmentId(), snapshot.getDepartmentName(), snapshot.getColumnName(),
                batch.getSnapshotType(), batch.getSnapshotDate(), batch.getCapturedAt(), batch.isRecovered());
    }
}
