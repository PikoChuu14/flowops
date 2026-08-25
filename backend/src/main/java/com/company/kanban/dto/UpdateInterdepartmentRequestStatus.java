package com.company.kanban.dto;
import com.company.kanban.entity.InterdepartmentRequestStatus;
import jakarta.validation.constraints.NotNull;
public record UpdateInterdepartmentRequestStatus(@NotNull InterdepartmentRequestStatus status, String note, String rejectionReason) {}
