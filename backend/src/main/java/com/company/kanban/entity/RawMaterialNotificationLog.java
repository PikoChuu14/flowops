package com.company.kanban.entity;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="raw_material_notification_logs", uniqueConstraints=@UniqueConstraint(name="uk_raw_material_notification_day", columnNames={"raw_material_arrival_id","notification_kind","notification_date"}))
public class RawMaterialNotificationLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="raw_material_arrival_id",nullable=false) private Long rawMaterialArrivalId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private com.company.kanban.entity.NotificationType notificationKind;
 @Column(nullable=false) private LocalDate notificationDate; @Column(nullable=false) private LocalDateTime createdAt;
 protected RawMaterialNotificationLog(){}
 public RawMaterialNotificationLog(Long arrivalId,com.company.kanban.entity.NotificationType kind,LocalDate date){rawMaterialArrivalId=arrivalId;notificationKind=kind;notificationDate=date;createdAt=LocalDateTime.now();}
 public Long getId(){return id;} public Long getRawMaterialArrivalId(){return rawMaterialArrivalId;} public com.company.kanban.entity.NotificationType getNotificationKind(){return notificationKind;} public LocalDate getNotificationDate(){return notificationDate;}
}
