package com.stocksaas.controller;

import com.stocksaas.dto.PlatformSettingsDTO;
import com.stocksaas.dto.ProofResourceResult;
import com.stocksaas.service.PlatformPaymentQrService;
import com.stocksaas.service.PlatformSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
                "https://sen-stocksaas.com",
                "http://164.132.43.247",
                "http://164.132.43.247:4200"
        },
        allowCredentials = "true"
)
public class PlatformSettingsController {

    private final PlatformSettingsService platformSettingsService;
    private final PlatformPaymentQrService platformPaymentQrService;

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

    @PostMapping(value = "/payment-qr/{provider}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Téléverser le QR code marchand Wave ou Orange Money")
    public ResponseEntity<PlatformSettingsDTO> uploadPaymentQr(
            @PathVariable String provider,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        requireAuthenticated(userDetails);
        platformPaymentQrService.uploadPaymentQr(provider, image);
        return ResponseEntity.ok(platformSettingsService.getSettings(userDetails.getUsername()));
    }

    @DeleteMapping("/payment-qr/{provider}")
    @Operation(summary = "Supprimer le QR code marchand")
    public ResponseEntity<PlatformSettingsDTO> deletePaymentQr(
            @PathVariable String provider,
            @AuthenticationPrincipal UserDetails userDetails) {
        requireAuthenticated(userDetails);
        platformPaymentQrService.deletePaymentQr(provider);
        return ResponseEntity.ok(platformSettingsService.getSettings(userDetails.getUsername()));
    }

    @GetMapping("/payment-qr/{provider}")
    @Operation(summary = "Aperçu du QR code marchand")
    public ResponseEntity<Resource> getPaymentQrPreview(
            @PathVariable String provider,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        requireAuthenticated(userDetails);
        ProofResourceResult qr = platformPaymentQrService.loadPaymentQr(provider);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(qr.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"qr-" + provider.toLowerCase() + "\"")
                .body(qr.resource());
    }

    private void requireAuthenticated(UserDetails userDetails) {
        if (userDetails == null || userDetails.getUsername() == null) {
            throw new RuntimeException("Non authentifié");
        }
    }
}
