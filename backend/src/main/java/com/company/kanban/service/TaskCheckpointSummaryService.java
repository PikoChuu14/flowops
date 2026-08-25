package com.company.kanban.service;

import com.company.kanban.dto.TaskCheckpointSummaryResponse;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskCheckpointSummaryService {
    private final TaskRepository tasks; private final TaskCheckpointRepository checkpoints; private final AuthorizationService authorization;
    public TaskCheckpointSummaryService(TaskRepository tasks, TaskCheckpointRepository checkpoints, AuthorizationService authorization){this.tasks=tasks;this.checkpoints=checkpoints;this.authorization=authorization;}
    @Transactional(readOnly=true)
    public List<TaskCheckpointSummaryResponse> summaries(List<Long> ids, User user){
        if(ids==null||ids.isEmpty()) return List.of();
        List<TaskCheckpoint> rows=checkpoints.findByTaskIdIn(ids);
        Map<Long,Long> totals=rows.stream().collect(Collectors.groupingBy(c->c.getTask().getId(),Collectors.counting()));
        Map<Long,Long> completed=rows.stream().filter(c->c.getStatus()==CheckpointStatus.COMPLETED).collect(Collectors.groupingBy(c->c.getTask().getId(),Collectors.counting()));
        Map<Long,TaskCheckpoint> current=rows.stream().collect(Collectors.groupingBy(c->c.getTask().getId(),Collectors.collectingAndThen(Collectors.toList(), list -> list.stream().sorted(Comparator.comparing(TaskCheckpoint::getPosition)).filter(c->c.getStatus()==CheckpointStatus.IN_PROGRESS).findFirst().orElseGet(()->list.stream().sorted(Comparator.comparing(TaskCheckpoint::getPosition)).filter(c->c.getStatus()==CheckpointStatus.PENDING).findFirst().orElse(null)))));
        return tasks.findAllById(ids).stream().filter(t->{authorization.requireTaskAccess(user,t);return t.getDepartment()!=null&&"RDD".equalsIgnoreCase(t.getDepartment().getName());}).map(t->{int total=totals.getOrDefault(t.getId(),0L).intValue();int done=completed.getOrDefault(t.getId(),0L).intValue();TaskCheckpoint next=current.get(t.getId());return new TaskCheckpointSummaryResponse(t.getId(),done,total,total==0?null:Math.round(done*100f/total),next==null?null:next.getStatus()==CheckpointStatus.IN_PROGRESS?"Current checkpoint":"Next checkpoint",next==null?null:next.getTitle());}).toList();
    }
}
