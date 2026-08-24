package com.company.kanban.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "raw_material_arrivals")
public class RawMaterialArrival {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 160) private String materialName;
    @Column(nullable = false, length = 160) private String supplierName;
    @Column(length = 80) private String poNumber;
    @Column private java.math.BigDecimal quantity;
    @Column(length = 32) private String unit;
    @Column(nullable = false) private LocalDate expectedArrivalDate;
    @Column private LocalDate actualArrivalDate;
    @Column(length = 2000) private String remarks;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'NOT_CONTACTED'") private RawMaterialFollowUpStatus followUpStatus = RawMaterialFollowUpStatus.NOT_CONTACTED;
    private LocalDateTime lastFollowUpAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "last_follow_up_by_id") private User lastFollowUpBy;
    @Column(length = 2000) private String followUpNote;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_id", nullable = false) private User createdBy;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected RawMaterialArrival() { }
    public RawMaterialArrival(String materialName, String supplierName, String poNumber, java.math.BigDecimal quantity,
                               String unit, LocalDate expectedArrivalDate, LocalDate actualArrivalDate, String remarks, User createdBy) {
        this.materialName = materialName; this.supplierName = supplierName; this.poNumber = poNumber; this.quantity = quantity;
        this.unit = unit; this.expectedArrivalDate = expectedArrivalDate; this.actualArrivalDate = actualArrivalDate;
        this.remarks = remarks; this.createdBy = createdBy; this.createdAt = LocalDateTime.now(); this.updatedAt = this.createdAt;
    }
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); if (updatedAt == null) updatedAt = createdAt; }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId(){return id;} public String getMaterialName(){return materialName;} public void setMaterialName(String v){materialName=v;}
    public String getSupplierName(){return supplierName;} public void setSupplierName(String v){supplierName=v;} public String getPoNumber(){return poNumber;} public void setPoNumber(String v){poNumber=v;}
    public java.math.BigDecimal getQuantity(){return quantity;} public void setQuantity(java.math.BigDecimal v){quantity=v;} public String getUnit(){return unit;} public void setUnit(String v){unit=v;}
    public LocalDate getExpectedArrivalDate(){return expectedArrivalDate;} public void setExpectedArrivalDate(LocalDate v){expectedArrivalDate=v;} public LocalDate getActualArrivalDate(){return actualArrivalDate;} public void setActualArrivalDate(LocalDate v){actualArrivalDate=v;}
    public String getRemarks(){return remarks;} public void setRemarks(String v){remarks=v;} public User getCreatedBy(){return createdBy;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public RawMaterialFollowUpStatus getFollowUpStatus(){return followUpStatus;} public void setFollowUpStatus(RawMaterialFollowUpStatus v){followUpStatus=v;}
    public LocalDateTime getLastFollowUpAt(){return lastFollowUpAt;} public User getLastFollowUpBy(){return lastFollowUpBy;} public String getFollowUpNote(){return followUpNote;}
    public void recordFollowUp(RawMaterialFollowUpStatus status, LocalDateTime at, User by, String note){followUpStatus=status;lastFollowUpAt=at;lastFollowUpBy=by;followUpNote=note;}
}
