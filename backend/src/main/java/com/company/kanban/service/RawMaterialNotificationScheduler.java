package com.company.kanban.service;
import org.springframework.context.event.EventListener; import org.springframework.boot.context.event.ApplicationReadyEvent; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Component public class RawMaterialNotificationScheduler {
 private final RawMaterialNotificationService service; public RawMaterialNotificationScheduler(RawMaterialNotificationService service){this.service=service;}
 @Scheduled(cron="0 0 8 * * *",zone="Asia/Kuala_Lumpur") public void dailyCheck(){service.runCheck();}
 @EventListener(ApplicationReadyEvent.class) public void recoverOnStartup(){service.runCheck();}
}
