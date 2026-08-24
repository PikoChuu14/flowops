package com.company.kanban.controller;

import com.company.kanban.dto.AgentNotificationResponse;
import com.company.kanban.entity.User;
import com.company.kanban.service.AgentNotificationService;
import com.company.kanban.service.DeviceService;
import com.company.kanban.entity.UserDevice;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class AgentNotificationController {
    private final AgentNotificationService notifications;
    private final DeviceService devices;
    public AgentNotificationController(AgentNotificationService notifications, DeviceService devices) { this.notifications = notifications; this.devices = devices; }

    @GetMapping("/notifications")
    public List<AgentNotificationResponse> notifications(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "100") int limit) {
        return notifications.after(user, after, limit);
    }

    @PostMapping("/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeCurrent(Authentication authentication) { devices.revokeAuthenticated((UserDevice) authentication.getDetails()); }
}
