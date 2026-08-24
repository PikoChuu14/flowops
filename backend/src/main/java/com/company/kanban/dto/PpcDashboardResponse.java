package com.company.kanban.dto;

import java.time.LocalDate;
import java.util.List;

public record PpcDashboardResponse(
        TaskSummary taskSummary,
        RawMaterialSummary rawMaterialAttention,
        List<PlanningPreview> upcomingPlanning,
        KanbanSummary kanbanSummary,
        List<OverdueTask> overdueTasks,
        long pendingReviews,
        List<TeamWorkload> teamWorkload,
        MonthlyReportStatus monthlyReportStatus
) {
    public record TaskSummary(long active, long dueToday, long overdue) {}
    public record RawMaterialSummary(long delayed, long dueToday, long arrivingTomorrow, long followUpDue,
                                     List<RawMaterialPreview> urgentItems) {}
    public record RawMaterialPreview(Long id, String materialName, LocalDate expectedArrivalDate,
                                     String attention, long delayDays) {}
    public record PlanningPreview(Long id, String title, LocalDate startDate, LocalDate endDate,
                                  String priority, String status) {}
    public record KanbanSummary(long toDo, long inProgress, long review, long done) {}
    public record OverdueTask(Long id, String title, String assigneeName, long overdueDays, Integer workload) {}
    public record TeamWorkload(Long userId, String name, long activeTaskCount, int activeWorkload) {}
    public record MonthlyReportStatus(int submitted, int total, List<String> pendingNames) {}
}
