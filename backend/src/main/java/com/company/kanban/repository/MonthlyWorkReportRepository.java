package com.company.kanban.repository;

import com.company.kanban.entity.MonthlyWorkReport;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyWorkReportRepository extends JpaRepository<MonthlyWorkReport, Long> {
    Optional<MonthlyWorkReport> findByUserIdAndYearAndMonth(Long userId, int year, int month);
    List<MonthlyWorkReport> findByUserIdInAndYearAndMonth(Collection<Long> userIds, int year, int month);
}
