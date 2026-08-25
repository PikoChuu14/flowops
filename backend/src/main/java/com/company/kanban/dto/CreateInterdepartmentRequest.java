package com.company.kanban.dto;
import com.company.kanban.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
public record CreateInterdepartmentRequest(@NotBlank String title, @NotBlank String description, @NotNull Priority priority, LocalDate neededBy, String targetDepartment) {}
