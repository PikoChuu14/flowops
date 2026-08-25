package com.company.kanban.dto;
import com.company.kanban.entity.CheckpointStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
public record UpdateCheckpointRequest(@NotBlank @Size(max=180) String title, @Size(max=1000) String description, LocalDate dueDate, CheckpointStatus status, Integer position) {}
