package com.company.kanban.repository;
import com.company.kanban.entity.TaskCheckpoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TaskCheckpointRepository extends JpaRepository<TaskCheckpoint, Long> { List<TaskCheckpoint> findByTaskIdOrderByPositionAsc(Long taskId); List<TaskCheckpoint> findByTaskIdIn(List<Long> taskIds); int countByTaskId(Long taskId); long countByTaskIdAndStatus(Long taskId, com.company.kanban.entity.CheckpointStatus status); }
