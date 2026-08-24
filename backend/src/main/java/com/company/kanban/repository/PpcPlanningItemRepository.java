package com.company.kanban.repository;

import com.company.kanban.entity.PpcPlanningItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PpcPlanningItemRepository extends JpaRepository<PpcPlanningItem, Long> {
    @Query("select item from PpcPlanningItem item where item.startDate <= :monthEnd and item.endDate >= :monthStart order by item.startDate, item.id")
    List<PpcPlanningItem> findIntersecting(@Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);

    @Query("select item from PpcPlanningItem item where item.endDate >= :from order by item.startDate, item.id")
    List<PpcPlanningItem> findUpcoming(@Param("from") LocalDate from, org.springframework.data.domain.Pageable pageable);
}
