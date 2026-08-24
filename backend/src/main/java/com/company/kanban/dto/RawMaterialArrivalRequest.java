package com.company.kanban.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record RawMaterialArrivalRequest(@NotBlank @Size(max=160) String materialName, @NotBlank @Size(max=160) String supplierName,
        @Size(max=80) String poNumber, @DecimalMin(value="0", inclusive=true) BigDecimal quantity, @Size(max=32) String unit,
        @NotNull LocalDate expectedArrivalDate, LocalDate actualArrivalDate, @Size(max=2000) String remarks) { }
