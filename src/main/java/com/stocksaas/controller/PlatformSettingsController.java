package com.stocksaas.controller;

import com.stocksaas.dto.PlatformSettingsDTO;
import com.stocksaas.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Paramètres plateforme", description = "Configuration globale — super administrateur")
@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://sen-stocksaas.com",
                "https://sen-stocksaas.com"
        },
        allowCredentials = "true"
)
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping
    @Operation(summary = "Lire les paramètres plateforme")
    public ResponseEntity<PlatformSettingsDTO> getSettings(@AuthenticationPrincipal UserDetails userDetails) {
        requireAuthenticated(userDetails);
        return ResponseEntity.ok(platformSettingsService.getSettings(userDetails.getUsername()));
    }

    @PutMapping
    @Operation(summary = "Mettre à jour les paramètres plateforme")
    public ResponseEntity<PlatformSettingsDTO> updateSettings(
            @Valid @RequestBody PlatformSettingsDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        requireAuthenticated(userDetails);
        return ResponseEntity.ok(platformSettingsService.updateSettings(body, userDetails.getUsername()));
    }

    private void requireAuthenticated(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null) {
            throw new RuntimeException("Non authentifié");
        }
    }
}
