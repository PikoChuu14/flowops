package com.company.kanban.dto;

import com.company.kanban.entity.Role;

public record CreateUserRequest(
        String name,
        String email,
        String password,
        Role role,
        Long departmentId
) {
}
