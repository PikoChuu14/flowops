package com.company.kanban.service;

import com.company.kanban.dto.RawMaterialArrivalRequest;
import com.company.kanban.entity.*;
import com.company.kanban.repository.RawMaterialArrivalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RawMaterialArrivalServiceTest {
    private final RawMaterialArrivalRepository repository = mock(RawMaterialArrivalRepository.class);
    private final AuthorizationService authorization = new AuthorizationService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneId.of("Asia/Kuala_Lumpur"));
    private RawMaterialArrivalService service;
    private User ppc;

    @BeforeEach void setUp() {
        Department department = entity(new Department("PPC"), 1L);
        ppc = entity(new User("PPC", "ppc@test", "x", Role.STAFF, department), 2L);
        service = new RawMaterialArrivalService(repository, authorization, clock);
        when(repository.save(any())).thenAnswer(i -> { RawMaterialArrival x=i.getArgument(0); if(x.getId()==null) ReflectionTestUtils.setField(x,"id",10L); return x; });
    }

    @Test void createsWithPrincipalAndDerivesExpected() { var response=service.create(request(LocalDate.of(2026,8,25),null),ppc); assertEquals(2L,response.createdById()); assertEquals(RawMaterialArrivalStatus.EXPECTED,response.status()); }
    @Test void derivesDueTodayAndDelayedDays() { assertEquals(RawMaterialArrivalStatus.DUE_TODAY,service.create(request(LocalDate.of(2026,8,23),null),ppc).status()); assertEquals(3,service.create(request(LocalDate.of(2026,8,20),null),ppc).delayDays()); }
    @Test void derivesArrivedStatusesAndDays() { assertEquals(RawMaterialArrivalStatus.ARRIVED_ON_TIME,service.create(request(LocalDate.of(2026,8,23),LocalDate.of(2026,8,22)),ppc).status()); var late=service.create(request(LocalDate.of(2026,8,20),LocalDate.of(2026,8,23)),ppc); assertEquals(RawMaterialArrivalStatus.ARRIVED_LATE,late.status()); assertEquals(3,late.delayDays()); }
    @Test void nonPpcAndValidationAreRejected() { User prod=new User("Prod","prod@test","x",Role.STAFF,new Department("PROD")); assertThrows(ResponseStatusException.class,()->service.create(request(LocalDate.now(),null),prod)); assertThrows(ResponseStatusException.class,()->service.create(new RawMaterialArrivalRequest(" ","Supplier",null,null,null,LocalDate.now(),null,null),ppc)); }
    @Test void monthAndMarkArrivedAndDeleteWork() { RawMaterialArrival x=new RawMaterialArrival("Resin","Supplier",null,null,null,LocalDate.of(2026,8,20),null,null,ppc); ReflectionTestUtils.setField(x,"id",10L); when(repository.findForMonth(LocalDate.of(2026,8,1),LocalDate.of(2026,8,31))).thenReturn(List.of(x)); assertEquals(1,service.getItems(2026,8,ppc).size()); when(repository.findById(10L)).thenReturn(Optional.of(x)); assertEquals(RawMaterialArrivalStatus.ARRIVED_LATE,service.markArrived(10L,LocalDate.of(2026,8,23),ppc).status()); when(repository.existsById(10L)).thenReturn(true); service.delete(10L,ppc); verify(repository).deleteById(10L); }
    @Test void editingEtaRecalculatesStatus() { RawMaterialArrival x=new RawMaterialArrival("Resin","Supplier",null,null,null,LocalDate.of(2026,8,20),null,null,ppc); ReflectionTestUtils.setField(x,"id",10L); when(repository.findById(10L)).thenReturn(Optional.of(x)); var response=service.update(10L,request(LocalDate.of(2026,8,25),null),ppc); assertEquals(RawMaterialArrivalStatus.EXPECTED,response.status()); }
    @Test void followUpStoresAuthenticatedActorTimestampAndNote() { RawMaterialArrival x=new RawMaterialArrival("Resin","Supplier",null,null,null,LocalDate.of(2026,8,20),null,null,ppc); ReflectionTestUtils.setField(x,"id",10L); when(repository.findById(10L)).thenReturn(Optional.of(x)); var response=service.followUp(10L,new com.company.kanban.dto.RawMaterialFollowUpRequest(RawMaterialFollowUpStatus.CONTACTED_SUPPLIER," Called supplier "),ppc); assertEquals(RawMaterialFollowUpStatus.CONTACTED_SUPPLIER,response.followUpStatus()); assertEquals(2L,response.lastFollowUpById()); assertEquals("Called supplier",response.followUpNote()); assertNotNull(response.lastFollowUpAt()); }
    private RawMaterialArrivalRequest request(LocalDate eta,LocalDate actual){return new RawMaterialArrivalRequest(" Resin "," Supplier ",null,BigDecimal.ONE,"kg",eta,actual," note ");}
    private <T>T entity(T value,Long id){ReflectionTestUtils.setField(value,"id",id);return value;}
}
