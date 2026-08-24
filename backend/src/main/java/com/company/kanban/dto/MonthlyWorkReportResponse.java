package com.company.kanban.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MonthlyWorkReportResponse(
        Period period, Employee employee, Narrative report, Overview overview,
        List<TaskItem> completedTasks, List<TaskItem> ongoingTasks) {
    public record Period(int year, int month, LocalDate start, LocalDate end) {}
    public record Employee(Long userId, String userName, String role, Long departmentId, String departmentName) {}
    public record Narrative(String status, String monthlySummary, String keyAchievements, String blockers, String nextMonthPlan, LocalDateTime submittedAt) {}
    public record Overview(int completedCount, int ongoingCount, int activeWorkload, int projectsWorkedOn) {}
    public record TaskItem(Long taskId, String title, String projectName, String status, Integer workload, LocalDate completionDate) {}
}
