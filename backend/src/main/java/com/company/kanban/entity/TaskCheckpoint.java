package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_checkpoints", indexes = @Index(name = "idx_checkpoint_task_position", columnList = "task_id,position"))
public class TaskCheckpoint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id", nullable = false) private Task task;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 1000) private String description;
    private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CheckpointStatus status = CheckpointStatus.PENDING;
    @Column(nullable = false) private Integer position;
    private LocalDateTime completedAt;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    protected TaskCheckpoint() {}
    public TaskCheckpoint(Task task, String title, String description, LocalDate dueDate, int position) { this.task=task; this.title=title; this.description=description; this.dueDate=dueDate; this.position=position; }
    @PrePersist void create(){if(createdAt==null)createdAt=LocalDateTime.now();if(updatedAt==null)updatedAt=createdAt;}
    @PreUpdate void update(){updatedAt=LocalDateTime.now();}
    public Long getId(){return id;} public Task getTask(){return task;} public String getTitle(){return title;} public String getDescription(){return description;} public LocalDate getDueDate(){return dueDate;} public CheckpointStatus getStatus(){return status;} public Integer getPosition(){return position;} public LocalDateTime getCompletedAt(){return completedAt;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public void setTitle(String v){title=v;} public void setDescription(String v){description=v;} public void setDueDate(LocalDate v){dueDate=v;} public void setStatus(CheckpointStatus v){status=v;} public void setPosition(Integer v){position=v;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}
