package com.company.kanban.repository;

import com.company.kanban.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;
import com.company.kanban.entity.TaskStatus;

public interface TaskRepository
        extends JpaRepository<Task, Long> {

    List<Task> findByColumnIdOrderByPositionAsc(Long columnId);

    List<Task> findByColumnIdAndIdNotOrderByPositionAsc(
            Long columnId,
            Long taskId
    );

    List<Task> findByAssigneeIdOrderByStatusAscPositionAsc(Long assigneeId);

    List<Task> findByStatusOrderBySubmittedForReviewAtAsc(com.company.kanban.entity.TaskStatus status);

    int countByColumnId(Long columnId);

    int countByColumnIsNullAndStatus(TaskStatus status);

    List<Task> findByColumnIsNullAndStatusAndIdNotOrderByPositionAsc(TaskStatus status, Long taskId);

    boolean existsByColumnBoardId(Long boardId);

    boolean existsByAssigneeIdOrCreatedById(Long assigneeId, Long createdById);

    @org.springframework.data.jpa.repository.Query("""
            select distinct t from Task t
            left join fetch t.column c
            left join fetch c.board b
            left join fetch b.department bd
            left join fetch t.department td
            left join fetch t.assignee a
            left join fetch t.createdBy cb
            where bd.id = :departmentId or td.id = :departmentId
            order by t.status, t.position
            """)
    List<Task> findByEffectiveDepartmentId(Long departmentId);

    @org.springframework.data.jpa.repository.Query("""
            select distinct t from Task t
            left join fetch t.column c
            left join fetch c.board b
            left join fetch b.department d
            left join fetch t.department td
            left join fetch t.assignee a
            left join fetch t.createdBy cb
            where t.status <> com.company.kanban.entity.TaskStatus.DONE
               or (t.updatedAt >= :from and t.updatedAt < :to)
            order by t.id
            """)
    List<Task> findRelevantForSnapshot(LocalDateTime from, LocalDateTime to);
}
