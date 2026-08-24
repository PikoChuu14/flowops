package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RawMaterialNotificationServiceTest {
    private final RawMaterialArrivalRepository arrivals=mock(RawMaterialArrivalRepository.class);
    private final RawMaterialNotificationLogRepository logs=mock(RawMaterialNotificationLogRepository.class);
    private final NotificationRepository notifications=mock(NotificationRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-08-24T04:00:00Z"),RawMaterialNotificationService.ZONE);
    private RawMaterialNotificationService service; private User staff; private RawMaterialArrival arrival;

    @BeforeEach void setUp(){
        service=new RawMaterialNotificationService(arrivals,logs,notifications,users,clock);
        Department ppc=entity(new Department("PPC"),1L); staff=entity(new User("Bob","bob@test","x",Role.STAFF,ppc),2L);
        User disabled=entity(new User("Disabled","disabled@test","x",Role.STAFF,ppc),3L);disabled.setStatus(AccountStatus.DISABLED);
        User pending=entity(new User("Pending","pending@test","x",Role.MANAGER,ppc),4L);pending.setStatus(AccountStatus.PENDING_ACTIVATION);
        when(users.findByStatus(AccountStatus.ACTIVE)).thenReturn(List.of(staff));
        when(logs.existsByRawMaterialArrivalIdAndNotificationKindAndNotificationDate(anyLong(),any(),any())).thenReturn(false);
        arrival=entity(new RawMaterialArrival("Resin A","ABC Materials",null,null,null,LocalDate.of(2026,8,25),null,null,staff),10L);
    }
    @Test void tomorrowCreatesOnceAndNotifiesActivePpcOnly(){when(arrivals.findAll()).thenReturn(List.of(arrival));service.runCheck();verify(notifications).save(any(Notification.class));verify(logs).save(any());service.runCheck();verify(notifications,times(2)).save(any(Notification.class));}
    @Test void todayAndDelayedNotContactedCreateExpectedTypes(){arrival.setExpectedArrivalDate(LocalDate.of(2026,8,24));when(arrivals.findAll()).thenReturn(List.of(arrival));service.runCheck();var captor=org.mockito.ArgumentCaptor.forClass(Notification.class);verify(notifications).save(captor.capture());assertEquals(NotificationType.RAW_MATERIAL_DUE_TODAY,captor.getValue().getType());arrival.setExpectedArrivalDate(LocalDate.of(2026,8,20));service.runCheck();verify(notifications,times(2)).save(any());}
    @Test void contactedAndWaitingSuppressUntilStaleThenFollowUpDue(){arrival.setExpectedArrivalDate(LocalDate.of(2026,8,20));arrival.recordFollowUp(RawMaterialFollowUpStatus.CONTACTED_SUPPLIER,LocalDateTime.of(2026,8,23,10,0),staff,"Called supplier");when(arrivals.findAll()).thenReturn(List.of(arrival));service.runCheck();verify(notifications,never()).save(any());arrival.recordFollowUp(RawMaterialFollowUpStatus.WAITING_FOR_UPDATE,LocalDateTime.of(2026,8,21,10,0),staff,"Waiting");service.runCheck();verify(notifications).save(any(Notification.class));}
    @Test void resolvedAndArrivedSuppressAllReminderTypes(){arrival.setExpectedArrivalDate(LocalDate.of(2026,8,20));arrival.recordFollowUp(RawMaterialFollowUpStatus.RESOLVED,LocalDateTime.of(2026,8,20,10,0),staff,"Resolved");when(arrivals.findAll()).thenReturn(List.of(arrival));service.runCheck();verify(notifications,never()).save(any());arrival.setActualArrivalDate(LocalDate.of(2026,8,25));arrival.setFollowUpStatus(RawMaterialFollowUpStatus.NOT_CONTACTED);service.runCheck();verify(notifications,never()).save(any());}
    @Test void newEtaUsesNewReminderDate(){arrival.setExpectedArrivalDate(LocalDate.of(2026,8,26));when(arrivals.findAll()).thenReturn(List.of(arrival));service.runCheck();verify(notifications,never()).save(any());arrival.setExpectedArrivalDate(LocalDate.of(2026,8,25));service.runCheck();verify(notifications).save(any(Notification.class));}
    private <T>T entity(T value,Long id){ReflectionTestUtils.setField(value,"id",id);return value;}
}
