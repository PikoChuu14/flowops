package com.company.kanban.dto;
import com.company.kanban.entity.RawMaterialFollowUpStatus; import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Size;
public record RawMaterialFollowUpRequest(@NotNull RawMaterialFollowUpStatus status,@Size(max=2000) String note){}
