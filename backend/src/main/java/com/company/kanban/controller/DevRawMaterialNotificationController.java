package com.company.kanban.controller;
import com.company.kanban.entity.User; import com.company.kanban.service.AuthorizationService; import com.company.kanban.service.RawMaterialNotificationService; import org.springframework.context.annotation.Profile; import org.springframework.http.HttpStatus; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
@Profile("dev") @RestController @RequestMapping("/api/dev/ppc/raw-material-notifications") public class DevRawMaterialNotificationController {
 private final RawMaterialNotificationService service; private final AuthorizationService authorization;
 public DevRawMaterialNotificationController(RawMaterialNotificationService service,AuthorizationService authorization){this.service=service;this.authorization=authorization;}
 @PostMapping("/run") @ResponseStatus(HttpStatus.NO_CONTENT) public void run(@AuthenticationPrincipal User user){if(!authorization.isAdmin(user))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Admin access required");service.runCheck();}
}
