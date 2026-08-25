package com.company.kanban.dto;
import com.company.kanban.entity.CheckpointStatus;
import java.time.*;
public record TaskCheckpointResponse(Long id, Long taskId, String title, String description, LocalDate dueDate, CheckpointStatus status, Integer position, LocalDateTime completedAt, LocalDateTime createdAt, LocalDateTime updatedAt, int completedCount, int totalCount, Integer progressPercent, String currentOrNextTitle, String dueState) {}
