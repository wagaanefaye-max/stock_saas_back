package com.stocksaas.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksaas.service.PlatformSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Bloque l'accès API lorsque le mode maintenance est actif (sauf super administrateur).
 */
@Component
@RequiredArgsConstructor
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/public/",
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs",
            "/error"
    );

    private final PlatformSettingsService platformSettingsService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!platformSettingsService.isMaintenanceModeEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isSuperAdminAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("/api/auth/login".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("/api/auth/logout".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        writeMaintenanceResponse(response);
    }

    private boolean isPublicPath(String path) {
        if (path == null) {
            return false;
        }
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isSuperAdminAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPER_ADMIN"::equals);
    }

    private void writeMaintenanceResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String customMessage = platformSettingsService.getPublicStatus().getMaintenanceMessage();
        String displayMessage = customMessage != null && !customMessage.isBlank()
                ? customMessage
                : "La plateforme est en maintenance. Veuillez réessayer plus tard.";

        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "message", displayMessage,
                "error", "MaintenanceMode",
                "maintenanceMode", true,
                "maintenanceMessage", customMessage != null ? customMessage : ""
        ));
    }
}
