package com.company.kanban.config;

import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/** Optional, repeat-safe July sample data for local monthly-report review. */
@Component
@Profile("dev")
public class MonthlyReportDemoDataInitializer {
    private static final int YEAR = 2026;
    private static final int MONTH = 7;
    private final boolean enabled;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final BoardRepository boards;
    private final KanbanColumnRepository columns;
    private final TaskRepository tasks;
    private final MonthlyWorkReportRepository reports;

    public MonthlyReportDemoDataInitializer(@Value("${app.monthly.demo-data:false}") boolean enabled,
                                             DepartmentRepository departments, UserRepository users,
                                             BoardRepository boards, KanbanColumnRepository columns,
                                             TaskRepository tasks, MonthlyWorkReportRepository reports) {
        this.enabled=enabled; this.departments=departments; this.users=users; this.boards=boards; this.columns=columns; this.tasks=tasks; this.reports=reports;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!enabled) return;
        Department ppc=departments.findByNameIgnoreCase("PPC").orElseThrow(() -> new IllegalStateException("PPC department not found"));
        List<User> staff=users.findByDepartmentIdOrderByNameAsc(ppc.getId()).stream().filter(u -> u.getRole()==Role.STAFF).toList();
        Board board=boards.findByDepartmentId(ppc.getId()).stream().findFirst().orElseThrow(() -> new IllegalStateException("PPC board not found"));
        Map<String,KanbanColumn> workflow=new HashMap<>(); for(KanbanColumn column:columns.findByBoardIdOrderByPositionAsc(board.getId())) workflow.put(column.getName(),column);
        for(int index=0; index<staff.size(); index++) seedFor(staff.get(index),index,workflow);
    }

    private void seedFor(User user,int index,Map<String,KanbanColumn> workflow) {
        String prefix="July 2026 Sample - ";
        boolean exists=tasks.findByAssigneeIdOrderByStatusAscPositionAsc(user.getId()).stream().anyMatch(t -> t.getTitle().startsWith(prefix));
        if(!exists){
            saveTask(prefix+"Completed schedule review", "Review and publish the July production schedule.", TaskStatus.DONE, 4, workflow.get("Done"), user, LocalDate.of(2026,7,8), LocalDateTime.of(2026,7,18,16,30), false);
            saveTask(prefix+"Completed material coordination", "Close the July material readiness follow-up.", TaskStatus.DONE, 3, workflow.get("Done"), user, LocalDate.of(2026,7,11), LocalDateTime.of(2026,7,25,15,0), false);
            saveTask(prefix+"August capacity handover", "Document carry-over capacity risks for the next cycle.", TaskStatus.DOING, 3, workflow.get("In Progress"), user, LocalDate.of(2026,7,21), null, false);
            saveTask(prefix+"Supplier confirmation", "General-task follow-up on supplier delivery timing.", TaskStatus.REVIEW, 2, null, user, LocalDate.of(2026,7,14), null, true);
        }
        if(reports.findByUserIdAndYearAndMonth(user.getId(),YEAR,MONTH).isEmpty()){
            MonthlyWorkReport report=new MonthlyWorkReport(user,YEAR,MONTH);
            report.update("July focused on schedule stability, material readiness, and clear handover into August.",
                    "Published the production schedule and closed two material coordination actions.",
                    "Supplier confirmation arrived late and compressed the planning window.",
                    "Confirm August capacity, refresh supplier dates, and carry forward unresolved risks.");
            if(index % 4 != 3) report.submit();
            reports.save(report);
        }
    }

    private void saveTask(String title,String description,TaskStatus status,int workload,KanbanColumn column,User user,LocalDate createdDate,LocalDateTime completedAt,boolean general){
        Task task=new Task(title,description,Priority.MEDIUM,createdDate.plusDays(10),1,column,user);
        task.setDepartment(user.getDepartment()); task.setCreatedBy(user); task.setStatus(status); task.setWorkload(workload); task.setCreatedAt(createdDate.atTime(9,0)); task.setCompletedAt(completedAt); tasks.save(task);
    }
}
