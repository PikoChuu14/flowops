package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interdepartment_request_status_history", indexes = @Index(name = "idx_request_history_request", columnList = "request_id,changed_at"))
public class InterdepartmentRequestStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "request_id", nullable = false) private InterdepartmentRequest request;
    @Enumerated(EnumType.STRING) @Column(length = 20) private InterdepartmentRequestStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InterdepartmentRequestStatus toStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "changed_by_id", nullable = false) private User changedBy;
    @Column(nullable = false) private LocalDateTime changedAt;
    @Column(length = 1000) private String note;
    protected InterdepartmentRequestStatusHistory() {}
    public InterdepartmentRequestStatusHistory(InterdepartmentRequest request, InterdepartmentRequestStatus from, InterdepartmentRequestStatus to, User actor, String note) { this.request=request; fromStatus=from; toStatus=to; changedBy=actor; this.note=note; changedAt=LocalDateTime.now(); }
    public Long getId(){return id;} public InterdepartmentRequestStatus getFromStatus(){return fromStatus;} public InterdepartmentRequestStatus getToStatus(){return toStatus;} public User getChangedBy(){return changedBy;} public LocalDateTime getChangedAt(){return changedAt;} public String getNote(){return note;}
}
