package com.company.kanban.controller;

import com.company.kanban.dto.PpcDashboardResponse;
import com.company.kanban.entity.User;
import com.company.kanban.service.PpcDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/ppc")
public class PpcDashboardController {
    private final PpcDashboardService service;
    public PpcDashboardController(PpcDashboardService service) { this.service = service; }
    @GetMapping
    public PpcDashboardResponse get(@AuthenticationPrincipal User user) { return service.get(user); }
}
