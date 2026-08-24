package com.company.kanban.service;

import com.company.kanban.dto.CreatePpcPlanningItemRequest;
import com.company.kanban.dto.UpdatePpcPlanningItemRequest;
import com.company.kanban.entity.Department;
import com.company.kanban.entity.PpcPlanningItem;
import com.company.kanban.entity.Role;
import com.company.kanban.entity.User;
import com.company.kanban.repository.PpcPlanningItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PpcPlanningServiceTest {
    private final PpcPlanningItemRepository repository = mock(PpcPlanningItemRepository.class);
    private final PpcPlanningService service = new PpcPlanningService(repository, new AuthorizationService());
    private User ppcStaff;
    private User admin;

    @BeforeEach
    void setUp() {
        Department ppc = entity(new Department("PPC"), 10L);
        ppcStaff = entity(new User("PPC Staff", "staff@test", "x", Role.STAFF, ppc), 1L);
        admin = entity(new User("Admin", "admin@test", "x", Role.ADMIN, entity(new Department("PROD"), 20L)), 2L);
        when(repository.save(any())).thenAnswer(invocation -> {
            PpcPlanningItem item = invocation.getArgument(0);
            if (item.getId() == null) ReflectionTestUtils.setField(item, "id", 100L);
            return item;
        });
    }

    @Test void ppcStaffCanCreateAndCreatorComesFromPrincipal() {
        var response = service.create(new CreatePpcPlanningItemRequest("  Stock plan  ", "desc", LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 12), null, null), ppcStaff);
        assertEquals("Stock plan", response.title());
        assertEquals(1L, response.createdById());
    }

    @Test void adminCanCreate() { assertDoesNotThrow(() -> service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null, null), admin)); }

    @Test void nonPpcCannotCreate() {
        User prod = new User("Prod", "prod@test", "x", Role.STAFF, new Department("PROD"));
        assertThrows(ResponseStatusException.class, () -> service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null), prod));
        verify(repository, never()).save(any());
    }

    @Test void titleAndDateValidation() {
        assertThrows(ResponseStatusException.class, () -> service.create(new CreatePpcPlanningItemRequest("  ", null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null), ppcStaff));
        assertThrows(ResponseStatusException.class, () -> service.create(new CreatePpcPlanningItemRequest("Plan", null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), null, null), ppcStaff));
    }

    @Test void retrievalUsesIntersectionForSingleAndCrossMonthItems() {
        PpcPlanningItem single = item("Single", LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 12));
        PpcPlanningItem crossing = item("Crossing", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 2));
        when(repository.findIntersecting(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).thenReturn(List.of(single, crossing));
        assertEquals(2, service.getItems(2026, 8, ppcStaff).size());
        verify(repository).findIntersecting(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    @Test void updateAndDeleteWork() {
        PpcPlanningItem item = item("Old", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1));
        when(repository.findById(100L)).thenReturn(Optional.of(item)); when(repository.existsById(100L)).thenReturn(true);
        assertEquals("New", service.update(100L, new UpdatePpcPlanningItemRequest("New", null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3), null, "Planned"), ppcStaff).title());
        service.delete(100L, ppcStaff); verify(repository).deleteById(100L);
    }

    private PpcPlanningItem item(String title, LocalDate start, LocalDate end) { PpcPlanningItem item = new PpcPlanningItem(title, null, start, end, null, null, ppcStaff); return entity(item, 100L); }
    private <T> T entity(T value, Long id) { ReflectionTestUtils.setField(value, "id", id); return value; }
}
