package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_recipient_created", columnList = "recipient_user_id,created_at"),
        @Index(name = "idx_notification_recipient_cleared", columnList = "recipient_user_id,cleared_at")
})
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 700)
    private String message;

    private Long taskId;
    private Long boardId;
    private Long dailyReportId;
    private Long rawMaterialArrivalId;

    @Column(length = 500)
    private String destination;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime clearedAt;

    protected Notification() {}

    public Notification(User recipient, NotificationType type, String title, String message,
                        Long taskId, Long boardId, Long dailyReportId) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.message = message;
        this.taskId = taskId;
        this.boardId = boardId;
        this.dailyReportId = dailyReportId;
    }
    public Notification(User recipient, NotificationType type, String title, String message,
                        Long taskId, Long boardId, Long dailyReportId, Long rawMaterialArrivalId) {
        this(recipient, type, title, message, taskId, boardId, dailyReportId); this.rawMaterialArrivalId = rawMaterialArrivalId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getRecipient() { return recipient; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Long getTaskId() { return taskId; }
    public Long getBoardId() { return boardId; }
    public Long getDailyReportId() { return dailyReportId; }
    public Long getRawMaterialArrivalId() { return rawMaterialArrivalId; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getClearedAt() { return clearedAt; }
    public void markRead() { read = true; }
    public void clear() { clearedAt = LocalDateTime.now(); }
}
