package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_work_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_monthly_report_user_period", columnNames = {"user_id", "report_year", "report_month"}),
        indexes = @Index(name = "idx_monthly_report_period", columnList = "report_year, report_month"))
public class MonthlyWorkReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "report_year", nullable = false) private int year;
    @Column(name = "report_month", nullable = false) private int month;
    @Lob private String monthlySummary;
    @Lob private String keyAchievements;
    @Lob private String blockers;
    @Lob private String nextMonthPlan;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MonthlyWorkReportStatus status = MonthlyWorkReportStatus.DRAFT;
    private LocalDateTime submittedAt;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected MonthlyWorkReport() {}
    public MonthlyWorkReport(User user, int year, int month) { this.user=user; this.year=year; this.month=month; this.createdAt=LocalDateTime.now(); this.updatedAt=this.createdAt; }
    public Long getId(){return id;} public User getUser(){return user;} public int getYear(){return year;} public int getMonth(){return month;}
    public String getMonthlySummary(){return monthlySummary;} public String getKeyAchievements(){return keyAchievements;} public String getBlockers(){return blockers;} public String getNextMonthPlan(){return nextMonthPlan;}
    public MonthlyWorkReportStatus getStatus(){return status;} public LocalDateTime getSubmittedAt(){return submittedAt;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public void update(String summary,String achievements,String blockers,String plan){this.monthlySummary=summary;this.keyAchievements=achievements;this.blockers=blockers;this.nextMonthPlan=plan;this.updatedAt=LocalDateTime.now();}
    public void submit(){this.status=MonthlyWorkReportStatus.SUBMITTED;this.submittedAt=LocalDateTime.now();this.updatedAt=this.submittedAt;}
}
