package com.stocksaas.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksaas.exception.SubscriptionReadOnlyException;
import com.stocksaas.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Bloque les requêtes d'écriture lorsque l'abonnement de l'entreprise est expiré (lecture seule).
 */
@Component
@RequiredArgsConstructor
public class SubscriptionWriteAccessFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final Set<String> WRITE_ALLOWED_PREFIXES = Set.of(
            "/api/subscriptions",
            "/api/auth"
    );

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path != null && WRITE_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!subscriptionService.canWriteForUser(userDetails.getUsername())) {
                writeForbidden(response);
                return;
            }
        } catch (SubscriptionReadOnlyException e) {
            writeForbidden(response, e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        writeForbidden(response,
                "Votre période d'utilisation est terminée. Vous pouvez consulter vos données en lecture seule. Souscrivez à un abonnement pour réactiver les modifications.");
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "message", message,
                "error", "SubscriptionReadOnly",
                "readOnly", true
        ));
    }
}
