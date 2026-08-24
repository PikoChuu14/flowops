package com.company.kanban.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "task_snapshots", uniqueConstraints = @UniqueConstraint(
        name = "uk_task_snapshot_batch_task", columnNames = {"batch_id", "task_id"}
), indexes = {
        @Index(name = "idx_task_snapshot_batch", columnList = "batch_id"),
        @Index(name = "idx_task_snapshot_task", columnList = "task_id"),
        @Index(name = "idx_task_snapshot_assignee", columnList = "assignee_id"),
        @Index(name = "idx_task_snapshot_board", columnList = "board_id")
})
public class TaskSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private SnapshotBatch batch;

    @Column(name = "task_id", nullable = false)
    private Long taskId;
    @Column(nullable = false) private String title;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TaskStatus status;
    private Integer workload;
    @Enumerated(EnumType.STRING) private Priority priority;
    private LocalDate dueDate;
    @Column(nullable = false) private Integer position;
    private Long assigneeId;
    private String assigneeName;
    private Long createdById;
    private String createdByName;
    @Column(name = "board_id") private Long boardId;
    private String boardName;
    @Column(name = "department_id", nullable = false) private Long departmentId;
    @Column(nullable = false) private String departmentName;
    private String columnName;

    protected TaskSnapshot() {}

    public TaskSnapshot(SnapshotBatch batch, Task task) {
        this.batch = batch;
        this.taskId = task.getId();
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.status = task.getStatus();
        this.workload = task.getWorkload();
        this.priority = task.getPriority();
        this.dueDate = task.getDueDate();
        this.position = task.getPosition();
        Department department = task.getDepartment();
        if (task.getColumn() != null) {
            this.columnName = task.getColumn().getName();
            this.boardId = task.getColumn().getBoard().getId();
            this.boardName = task.getColumn().getBoard().getName();
        } else {
            this.columnName = task.getStatus().name();
        }
        this.departmentId = department.getId();
        this.departmentName = department.getName();
        if (task.getAssignee() != null) {
            this.assigneeId = task.getAssignee().getId();
            this.assigneeName = task.getAssignee().getName();
        }
        if (task.getCreatedBy() != null) {
            this.createdById = task.getCreatedBy().getId();
            this.createdByName = task.getCreatedBy().getName();
        }
    }

    public Long getId() { return id; }
    public SnapshotBatch getBatch() { return batch; }
    public Long getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public Integer getWorkload() { return workload; }
    public Priority getPriority() { return priority; }
    public LocalDate getDueDate() { return dueDate; }
    public Integer getPosition() { return position; }
    public Long getAssigneeId() { return assigneeId; }
    public String getAssigneeName() { return assigneeName; }
    public Long getCreatedById() { return createdById; }
    public String getCreatedByName() { return createdByName; }
    public Long getBoardId() { return boardId; }
    public String getBoardName() { return boardName; }
    public Long getDepartmentId() { return departmentId; }
    public String getDepartmentName() { return departmentName; }
    public String getColumnName() { return columnName; }

    // Used by the deterministic demo seeder to model movement between snapshots.
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setAssignee(Long id, String name) { this.assigneeId = id; this.assigneeName = name; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
}
