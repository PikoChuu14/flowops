package com.company.kanban.repository;
import com.company.kanban.entity.*; import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
public interface RawMaterialNotificationLogRepository extends JpaRepository<RawMaterialNotificationLog,Long> { boolean existsByRawMaterialArrivalIdAndNotificationKindAndNotificationDate(Long id,NotificationType kind,LocalDate date); }
