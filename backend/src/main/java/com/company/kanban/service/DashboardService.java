package com.company.kanban.service;

import com.company.kanban.dto.StaffWorkloadResponse;
import com.company.kanban.entity.Role;
import com.company.kanban.entity.Task;
import com.company.kanban.entity.TaskStatus;
import com.company.kanban.entity.User;
import com.company.kanban.repository.TaskRepository;
import com.company.kanban.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final AuthorizationService authorizationService;

    public DashboardService(
            UserRepository userRepository,
            TaskRepository taskRepository,
            AuthorizationService authorizationService) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<StaffWorkloadResponse> getTeamWorkload(User currentUser) {
        return getTeamWorkload(currentUser, null);
    }

    @Transactional(readOnly = true)
    public List<StaffWorkloadResponse> getTeamWorkload(User currentUser, Long departmentId) {
        authorizationService.requireWorkloadDashboardAccess(currentUser);

        Long effectiveDepartmentId = currentUser.getRole() == Role.ADMIN
                ? departmentId
                : currentUser.getDepartment().getId();

        if (effectiveDepartmentId != null && !authorizationService.canAccessDepartment(currentUser, effectiveDepartmentId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "You do not have permission to access this department's data");
        }

        List<User> staff = effectiveDepartmentId == null
                ? userRepository.findAll()
                : userRepository.findByDepartmentIdOrderByNameAsc(effectiveDepartmentId);

        return staff.stream()
                .filter(user -> user.getRole() == Role.STAFF)
                .map(this::toResponse)
                .toList();
    }

    private StaffWorkloadResponse toResponse(User staffUser) {
        List<Task> tasks = taskRepository
                .findByAssigneeIdOrderByStatusAscPositionAsc(staffUser.getId());

        int totalWorkload = tasks.stream()
                .filter(task -> isActive(task))
                .mapToInt(task -> task.getWorkload() == null ? 0 : task.getWorkload())
                .sum();

        return new StaffWorkloadResponse(
                staffUser.getId(),
                staffUser.getName(),
                staffUser.getEmail(),
                staffUser.getDepartment().getId(),
                staffUser.getDepartment().getName(),
                totalWorkload,
                tasks.stream().filter(this::isActive).count(),
                count(tasks, TaskStatus.DRAFT),
                count(tasks, TaskStatus.DOING),
                count(tasks, TaskStatus.REVIEW),
                count(tasks, TaskStatus.DONE)
        );
    }

    private long count(List<Task> tasks, TaskStatus status) {
        return tasks.stream()
                .filter(task -> task.getStatus() == status)
                .count();
    }

    private boolean isActive(Task task) {
        return task.getStatus() == TaskStatus.DRAFT
                || task.getStatus() == TaskStatus.DOING
                || task.getStatus() == TaskStatus.REVIEW;
    }
}
