package com.company.kanban.service;

import com.company.kanban.dto.CreatePpcPlanningItemRequest;
import com.company.kanban.dto.PpcPlanningItemResponse;
import com.company.kanban.dto.UpdatePpcPlanningItemRequest;
import com.company.kanban.entity.PpcPlanningItem;
import com.company.kanban.entity.User;
import com.company.kanban.repository.PpcPlanningItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class PpcPlanningService {
    private final PpcPlanningItemRepository repository;
    private final AuthorizationService authorizationService;

    public PpcPlanningService(PpcPlanningItemRepository repository, AuthorizationService authorizationService) {
        this.repository = repository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<PpcPlanningItemResponse> getItems(int year, int month, User user) {
        authorizationService.requirePpcPlanningAccess(user);
        LocalDate monthStart;
        try { monthStart = LocalDate.of(year, month, 1); }
        catch (java.time.DateTimeException exception) { throw badRequest("year and month must identify a valid calendar month"); }
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        return repository.findIntersecting(monthStart, monthEnd).stream().map(PpcPlanningItemResponse::from).toList();
    }

    @Transactional
    public PpcPlanningItemResponse create(CreatePpcPlanningItemRequest request, User user) {
        authorizationService.requirePpcPlanningAccess(user);
        validateDates(request.startDate(), request.endDate());
        String title = request.title().trim();
        if (title.isEmpty()) throw badRequest("Title is required");
        PpcPlanningItem item = new PpcPlanningItem(title, clean(request.description()), request.startDate(), request.endDate(),
                clean(request.priority()), clean(request.status()), user);
        return PpcPlanningItemResponse.from(repository.save(item));
    }

    @Transactional
    public PpcPlanningItemResponse update(Long id, UpdatePpcPlanningItemRequest request, User user) {
        authorizationService.requirePpcPlanningAccess(user);
        validateDates(request.startDate(), request.endDate());
        PpcPlanningItem item = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planning item not found"));
        String title = request.title().trim();
        if (title.isEmpty()) throw badRequest("Title is required");
        item.setTitle(title); item.setDescription(clean(request.description())); item.setStartDate(request.startDate()); item.setEndDate(request.endDate());
        item.setPriority(clean(request.priority())); item.setStatus(clean(request.status()));
        return PpcPlanningItemResponse.from(repository.save(item));
    }

    @Transactional
    public void delete(Long id, User user) {
        authorizationService.requirePpcPlanningAccess(user);
        if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Planning item not found");
        repository.deleteById(id);
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw badRequest("End date must be on or after start date");
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
