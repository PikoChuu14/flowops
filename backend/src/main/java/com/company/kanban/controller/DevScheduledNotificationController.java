package com.company.kanban.controller;

import com.company.kanban.entity.User;
import com.company.kanban.service.AuthorizationService;
import com.company.kanban.service.RawMaterialNotificationScheduler;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Profile("dev")
@RestController
@RequestMapping("/api/dev/notifications")
public class DevScheduledNotificationController {
    private final RawMaterialNotificationScheduler scheduler;
    private final AuthorizationService authorization;
    public DevScheduledNotificationController(RawMaterialNotificationScheduler scheduler, AuthorizationService authorization) {
        this.scheduler = scheduler; this.authorization = authorization;
    }
    @PostMapping("/run-scheduled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void run(@AuthenticationPrincipal User user) {
        if (!authorization.isAdmin(user)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        scheduler.runAll();
    }
}
