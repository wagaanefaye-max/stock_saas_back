package com.stocksaas.controller;

import com.stocksaas.dto.PlatformStatusDTO;
import com.stocksaas.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/platform")
@RequiredArgsConstructor
@Tag(name = "Plateforme (public)", description = "Statut public de la plateforme")
@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://sen-stocksaas.com",
                "https://sen-stocksaas.com",
                "https://www.sen-stocksaas.com",
                "http://164.132.43.247",
                "http://164.132.43.247:4200"
        },
        allowCredentials = "true"
)
public class PublicPlatformController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping("/status")
    @Operation(summary = "Statut plateforme", description = "Indique si la plateforme est en maintenance")
    public ResponseEntity<PlatformStatusDTO> getStatus() {
        return ResponseEntity.ok(platformSettingsService.getPublicStatus());
    }
}
