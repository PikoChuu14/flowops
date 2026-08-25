package com.company.kanban.dto;
public record TaskCheckpointSummaryResponse(Long taskId, int completedCount, int totalCount, Integer progressPercent, String currentOrNextLabel, String currentOrNextTitle) {}
