package com.company.kanban.dto;

import java.time.LocalDate;
import java.util.List;

public record MonthlyTeamReportResponse(int year, int month, LocalDate start, LocalDate end, Long departmentId, String departmentName, Summary summary, List<EmployeeSummary> employees) {
    public record Summary(int employees, int submitted, int completedTasks, int ongoingTasks) {}
    public record EmployeeSummary(Long userId, String userName, String status, int completedCount, int ongoingCount, int activeWorkload,
                                  String monthlySummary, String keyAchievements, String blockers, String nextMonthPlan) {}
}
