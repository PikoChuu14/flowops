package com.company.kanban.service;
import org.springframework.context.event.EventListener; import org.springframework.boot.context.event.ApplicationReadyEvent; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Component public class RawMaterialNotificationScheduler {
 private final RawMaterialNotificationService rawMaterials;
 private final ScheduledNotificationService scheduled;
 public RawMaterialNotificationScheduler(RawMaterialNotificationService rawMaterials,ScheduledNotificationService scheduled){this.rawMaterials=rawMaterials;this.scheduled=scheduled;}
 @Scheduled(cron="0 0 8 * * *",zone="Asia/Kuala_Lumpur") public void dailyCheck(){runAll();}
 @EventListener(ApplicationReadyEvent.class) public void recoverOnStartup(){runAll();}
 public void runAll(){scheduled.runCheck();rawMaterials.runCheck();}
}
