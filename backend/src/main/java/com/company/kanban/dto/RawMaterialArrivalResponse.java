package com.company.kanban.dto;
import com.company.kanban.entity.*;
import java.math.BigDecimal; import java.time.*;
public record RawMaterialArrivalResponse(Long id,String materialName,String supplierName,String poNumber,BigDecimal quantity,String unit,
        LocalDate expectedArrivalDate,LocalDate actualArrivalDate,String remarks,RawMaterialArrivalStatus status,long delayDays,RawMaterialFollowUpStatus followUpStatus,LocalDateTime lastFollowUpAt,Long lastFollowUpById,String lastFollowUpByName,String followUpNote,Long createdById,String createdByName,LocalDateTime createdAt,LocalDateTime updatedAt) {
    public static RawMaterialArrivalResponse from(RawMaterialArrival item, LocalDate today) {
        RawMaterialArrivalStatus status; long delay;
        if(item.getActualArrivalDate()!=null){ status=item.getActualArrivalDate().isAfter(item.getExpectedArrivalDate())?RawMaterialArrivalStatus.ARRIVED_LATE:RawMaterialArrivalStatus.ARRIVED_ON_TIME; delay=Math.max(0, item.getActualArrivalDate().toEpochDay()-item.getExpectedArrivalDate().toEpochDay()); }
        else { status=today.isBefore(item.getExpectedArrivalDate())?RawMaterialArrivalStatus.EXPECTED:today.equals(item.getExpectedArrivalDate())?RawMaterialArrivalStatus.DUE_TODAY:RawMaterialArrivalStatus.DELAYED; delay=Math.max(0,today.toEpochDay()-item.getExpectedArrivalDate().toEpochDay()); }
        return new RawMaterialArrivalResponse(item.getId(),item.getMaterialName(),item.getSupplierName(),item.getPoNumber(),item.getQuantity(),item.getUnit(),item.getExpectedArrivalDate(),item.getActualArrivalDate(),item.getRemarks(),status,delay,item.getFollowUpStatus()==null?RawMaterialFollowUpStatus.NOT_CONTACTED:item.getFollowUpStatus(),item.getLastFollowUpAt(),item.getLastFollowUpBy()==null?null:item.getLastFollowUpBy().getId(),item.getLastFollowUpBy()==null?null:item.getLastFollowUpBy().getName(),item.getFollowUpNote(),item.getCreatedBy().getId(),item.getCreatedBy().getName(),item.getCreatedAt(),item.getUpdatedAt());
    }
}
