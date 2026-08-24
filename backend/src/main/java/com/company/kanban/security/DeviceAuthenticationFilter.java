package com.company.kanban.security;

import com.company.kanban.entity.UserDevice;
import com.company.kanban.service.DeviceService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.List;

@Component
public class DeviceAuthenticationFilter extends OncePerRequestFilter {
    private final DeviceService devices;
    public DeviceAuthenticationFilter(DeviceService devices) { this.devices = devices; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/agent/"); }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Device ")) { chain.doFilter(request, response); return; }
        try {
            UserDevice device = devices.authenticate(header.substring(7).trim());
            var authority = new SimpleGrantedAuthority("ROLE_" + device.getUser().getRole().name());
            var auth = new UsernamePasswordAuthenticationToken(device.getUser(), null, List.of(authority));
            auth.setDetails(device);
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (ResponseStatusException ex) {
            SecurityContextHolder.clearContext();
            response.setHeader("X-FlowOps-Device-Status", "authentication-required");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
