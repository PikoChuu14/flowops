package com.company.kanban.controller;

import com.company.kanban.dto.DeviceDtos.*;
import com.company.kanban.entity.User;
import com.company.kanban.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService devices;
    private final ConcurrentHashMap<String, AttemptWindow> exchangeAttempts = new ConcurrentHashMap<>();
    public DeviceController(DeviceService devices) { this.devices = devices; }

    @PostMapping("/register-code")
    public RegistrationCodeResponse create(@AuthenticationPrincipal User user) { return devices.createRegistrationCode(user); }

    @PostMapping("/exchange")
    public ExchangeResponse exchange(@Valid @RequestBody ExchangeRequest request, HttpServletRequest servletRequest) {
        limitExchange(servletRequest.getRemoteAddr());
        return devices.exchange(request.code(), request.deviceName());
    }

    @GetMapping
    public List<DeviceResponse> list(@AuthenticationPrincipal User user) { return devices.list(user); }

    @PostMapping("/revoke/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id, @AuthenticationPrincipal User user) { devices.revoke(id, user); }

    private void limitExchange(String remoteAddress) {
        Instant now = Instant.now();
        exchangeAttempts.compute(remoteAddress == null ? "unknown" : remoteAddress, (key, current) -> {
            if (current == null || current.started().plus(Duration.ofMinutes(5)).isBefore(now)) return new AttemptWindow(now, 1);
            if (current.count() >= 10) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many device code attempts");
            return new AttemptWindow(current.started(), current.count() + 1);
        });
    }
    private record AttemptWindow(Instant started, int count) {}
}
