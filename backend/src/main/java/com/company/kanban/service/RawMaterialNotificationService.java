package com.company.kanban.service;

import com.company.kanban.dto.RawMaterialArrivalResponse;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class RawMaterialNotificationService {
    static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    static final long FOLLOW_UP_REMINDER_DAYS = 2;
    private final RawMaterialArrivalRepository arrivals;
    private final RawMaterialNotificationLogRepository logs;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    @Autowired public RawMaterialNotificationService(RawMaterialArrivalRepository arrivals, RawMaterialNotificationLogRepository logs,
                                                       NotificationRepository notifications, UserRepository users) {
        this(arrivals, logs, notifications, users, Clock.system(ZONE));
    }
    RawMaterialNotificationService(RawMaterialArrivalRepository arrivals, RawMaterialNotificationLogRepository logs,
                                   NotificationRepository notifications, UserRepository users, Clock clock) {
        this.arrivals=arrivals; this.logs=logs; this.notifications=notifications; this.users=users; this.clock=clock;
    }

    @Transactional
    public void runCheck() {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate tomorrow = today.plusDays(1);
        arrivals.findAll().stream().filter(x -> x.getActualArrivalDate() == null).forEach(x -> {
            if (x.getExpectedArrivalDate().equals(tomorrow)) create(x, NotificationType.RAW_MATERIAL_ARRIVING_TOMORROW, today,
                    "Raw material arriving tomorrow", x.getMaterialName()+" from "+x.getSupplierName()+" is expected on "+format(x.getExpectedArrivalDate())+".");
            if (x.getExpectedArrivalDate().equals(today)) create(x, NotificationType.RAW_MATERIAL_DUE_TODAY, today,
                    "Raw material expected today", x.getMaterialName()+" from "+x.getSupplierName()+" is expected today.");
            if (x.getExpectedArrivalDate().isBefore(today)) {
                RawMaterialFollowUpStatus followUp = x.getFollowUpStatus()==null ? RawMaterialFollowUpStatus.NOT_CONTACTED : x.getFollowUpStatus();
                if (followUp == RawMaterialFollowUpStatus.NOT_CONTACTED) {
                    long days = today.toEpochDay()-x.getExpectedArrivalDate().toEpochDay();
                    create(x, NotificationType.RAW_MATERIAL_DELAYED, today, "Raw material delayed",
                            x.getMaterialName()+" from "+x.getSupplierName()+" is delayed by "+days+" days. Supplier follow-up is required.");
                } else if ((followUp == RawMaterialFollowUpStatus.CONTACTED_SUPPLIER || followUp == RawMaterialFollowUpStatus.WAITING_FOR_UPDATE)
                        && x.getLastFollowUpAt()!=null && today.toEpochDay()-x.getLastFollowUpAt().atZone(ZONE).toLocalDate().toEpochDay() >= FOLLOW_UP_REMINDER_DAYS) {
                    create(x, NotificationType.RAW_MATERIAL_FOLLOW_UP_DUE, today, "Supplier follow-up due",
                            x.getMaterialName()+" remains delayed. Supplier was last contacted "+(today.toEpochDay()-x.getLastFollowUpAt().atZone(ZONE).toLocalDate().toEpochDay())+" days ago.");
                }
            }
        });
    }

    private void create(RawMaterialArrival arrival, NotificationType kind, LocalDate date, String title, String message) {
        if (logs.existsByRawMaterialArrivalIdAndNotificationKindAndNotificationDate(arrival.getId(), kind, date)) return;
        RawMaterialNotificationLog log = logs.save(new RawMaterialNotificationLog(arrival.getId(), kind, date));
        users.findByStatus(AccountStatus.ACTIVE).stream()
                .filter(u -> u.getDepartment()!=null && "PPC".equalsIgnoreCase(u.getDepartment().getName()))
                .filter(u -> u.getRole()==Role.STAFF || u.getRole()==Role.MANAGER)
                .forEach(u -> notifications.save(new Notification(u, kind, title, message, null, null, null, arrival.getId())));
    }
    private String format(LocalDate date){return date.getDayOfMonth()+" "+date.getMonth().toString().substring(0,1)+date.getMonth().toString().substring(1).toLowerCase()+" "+date.getYear();}
}
