package com.company.kanban.repository;

import com.company.kanban.entity.SnapshotBatch;
import com.company.kanban.entity.SnapshotBatchStatus;
import com.company.kanban.entity.SnapshotType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SnapshotBatchRepository extends JpaRepository<SnapshotBatch, Long> {
    Optional<SnapshotBatch> findBySnapshotDateAndSnapshotType(LocalDate date, SnapshotType type);
    List<SnapshotBatch> findByStatusOrderBySnapshotDateDesc(SnapshotBatchStatus status);
    List<SnapshotBatch> findAllByOrderBySnapshotDateDescSnapshotTypeAsc();
    List<SnapshotBatch> findBySnapshotDateBetweenAndStatusOrderBySnapshotDateAsc(LocalDate start, LocalDate end, SnapshotBatchStatus status);
}
