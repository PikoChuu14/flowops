package com.company.kanban.controller;

import com.company.kanban.dto.*;
import com.company.kanban.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/admin/users") @PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserService users; private final ActivationService activation;
    public AdminUserController(UserService users, ActivationService activation) { this.users=users; this.activation=activation; }
    @GetMapping public List<UserResponse> list() { return users.getAllUsers(); }
    @GetMapping("/{id}") public UserResponse get(@PathVariable Long id) { return users.getUserById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public UserResponse create(@RequestBody CreateUserRequest request) { return users.createUser(request); }
    @PutMapping("/{id}") public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest request) { return users.updateUser(id, request); }
    @PostMapping("/{id}/password") public UserResponse setPassword(@PathVariable Long id, @RequestBody SetUserPasswordRequest request) { return users.setPassword(id, request.password()); }
    @PostMapping("/{id}/disable") public UserResponse disable(@PathVariable Long id) { return users.disable(id); }
    @PostMapping("/{id}/reactivate") public UserResponse reactivate(@PathVariable Long id) { return users.reactivate(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { users.deleteDisabledUser(id); }
    @PostMapping("/{id}/activation-link") public ActivationLinkResponse activationLink(@PathVariable Long id) { return activation.createLink(id); }
}
