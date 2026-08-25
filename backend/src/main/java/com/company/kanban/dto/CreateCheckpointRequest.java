package com.company.kanban.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
public record CreateCheckpointRequest(@NotBlank @Size(max=180) String title, @Size(max=1000) String description, LocalDate dueDate) {}
