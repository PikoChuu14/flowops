package com.company.kanban.config;

import com.company.kanban.entity.Department;
import com.company.kanban.entity.PpcPlanningItem;
import com.company.kanban.entity.Role;
import com.company.kanban.entity.User;
import com.company.kanban.repository.DepartmentRepository;
import com.company.kanban.repository.PpcPlanningItemRepository;
import com.company.kanban.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Adds visual sample data only to local dev/demo databases. */
@Component
@Profile({"dev", "demo"})
public class PpcPlanningDemoDataInitializer {
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final PpcPlanningItemRepository planningItems;

    public PpcPlanningDemoDataInitializer(DepartmentRepository departments, UserRepository users,
                                           PpcPlanningItemRepository planningItems) {
        this.departments = departments;
        this.users = users;
        this.planningItems = planningItems;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void addDemoPlanningItems() {
        Department ppc = departments.findByNameIgnoreCase("PPC").orElse(null);
        User creator = ppc == null ? null : users.findByDepartmentIdAndRole(ppc.getId(), Role.MANAGER)
                .stream().findFirst().orElseGet(() -> users.findByDepartmentIdOrderByNameAsc(ppc.getId()).stream().findFirst().orElse(null));
        if (creator == null || planningItems.findAll().stream().anyMatch(item -> item.getTitle().startsWith("Demo "))) return;

        List<PpcPlanningItem> samples = List.of(
                item("Demo Monthly Capacity Review", "Review line capacity, planned downtime, and the production outlook with the planning team.", "2026-08-03", "2026-08-04", creator),
                item("Demo Customer Delivery Alignment", "Align promised delivery dates with the latest customer priorities and confirmed production slots.", "2026-08-05", "2026-08-07", creator),
                item("Demo Trial Production X", "Trial run for Product X including setup, first-off checks, and production feedback capture.", "2026-08-10", "2026-08-14", creator),
                item("Demo Weekly Schedule Update", "Publish the revised schedule after reviewing open orders and line availability.", "2026-08-11", "2026-08-11", creator),
                item("Demo Material Readiness Review", "Check material readiness against the next two weeks of planned production.", "2026-08-12", "2026-08-13", creator),
                item("Demo Production Planning Workshop", "Cross-functional workshop for resolving capacity conflicts and sequencing constraints.", "2026-08-17", "2026-08-19", creator),
                item("Demo Dispatch Plan", "Prepare and circulate the dispatch plan for the final August production window.", "2026-08-18", "2026-08-18", creator),
                item("Demo Forecast Refresh", "Refresh the rolling forecast using confirmed orders, current output, and latest demand changes.", "2026-08-20", "2026-08-21", creator),
                item("Demo Month-End Review", "Review plan achievement, carry-over work, and risks requiring follow-up next month.", "2026-08-24", "2026-08-25", creator),
                item("Demo September Kickoff", "Set the initial September priorities and confirm the first production slots.", "2026-08-27", "2026-08-31", creator),
                item("Demo Supplier Schedule Check", "Validate supplier delivery assumptions against the production plan and open commitments.", "2026-08-28", "2026-08-28", creator),
                item("Demo Planning Huddle", "Short planning huddle for the daily schedule, urgent changes, and unresolved constraints.", "2026-08-28", "2026-08-28", creator),
                item("Demo Risk Review", "Capture schedule risks and assign follow-up actions before the next planning cycle.", "2026-08-28", "2026-08-28", creator),
                item("Demo Backlog Prioritisation", "Prioritise the remaining backlog using customer urgency, readiness, and available capacity.", "2026-08-28", "2026-08-28", creator)
        );
        planningItems.saveAll(samples);
    }

    private PpcPlanningItem item(String title, String description, String start, String end, User creator) {
        return new PpcPlanningItem(title, description, LocalDate.parse(start), LocalDate.parse(end), null, null, creator);
    }
}
