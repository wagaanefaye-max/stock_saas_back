package com.stocksaas.controller;

import com.stocksaas.dto.RejectSubscriptionRequest;
import com.stocksaas.dto.SubscriptionDurationDTO;
import com.stocksaas.dto.SubscriptionPlanOptionDTO;
import com.stocksaas.dto.SubscriptionRecordDTO;
import com.stocksaas.dto.SubscriptionRequestsPageResponse;
import com.stocksaas.dto.SubscriptionStatusDTO;
import com.stocksaas.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Abonnements", description = "Essai gratuit, souscription avec justificatif et validation admin")
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
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/status")
    @Operation(summary = "Statut d'abonnement")
    public ResponseEntity<SubscriptionStatusDTO> getMyStatus(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.getStatusForCurrentUser(userDetails.getUsername()));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlanOptionDTO>> listPlans() {
        return ResponseEntity.ok(subscriptionService.listPaidPlans());
    }

    @GetMapping("/durations")
    public ResponseEntity<List<SubscriptionDurationDTO>> listDurations() {
        return ResponseEntity.ok(subscriptionService.listDurations());
    }

    @PostMapping(value = "/request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Demande de souscription", description = "Soumet une capture Wave ou Orange Money en attente de validation")
    public ResponseEntity<SubscriptionRecordDTO> submitRequest(
            @RequestParam String planCode,
            @RequestParam String durationCode,
            @RequestParam String paymentProvider,
            @RequestPart("proof") MultipartFile proof,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        SubscriptionStatusDTO status = subscriptionService.getStatusForCurrentUser(userDetails.getUsername());
        SubscriptionRecordDTO result = subscriptionService.submitSubscriptionRequest(
                status.getCompanyId(),
                planCode,
                durationCode,
                paymentProvider,
                proof,
                userDetails.getUsername()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    public ResponseEntity<List<SubscriptionRecordDTO>> listMyHistory(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.listHistoryForCurrentUser(userDetails.getUsername()));
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Toutes les souscriptions", description = "Super admin — pagination et filtre optionnel : PENDING, APPROVED, REJECTED")
    public ResponseEntity<SubscriptionRequestsPageResponse> listAllRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.listAllRequestsForAdminPaged(
                userDetails.getUsername(), status, page, size));
    }

    @GetMapping("/requests/pending")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Demandes en attente", description = "Super administrateur uniquement")
    public ResponseEntity<List<SubscriptionRecordDTO>> listPending(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.listPendingRequests(userDetails.getUsername()));
    }

    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Valider une souscription")
    public ResponseEntity<SubscriptionRecordDTO> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.approveRequest(id, userDetails.getUsername()));
    }

    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Refuser une souscription")
    public ResponseEntity<SubscriptionRecordDTO> reject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectSubscriptionRequest body,
            @AuthenticationPrincipal UserDetails userDetails) {
        String reason = body != null ? body.getReason() : null;
        return ResponseEntity.ok(subscriptionService.rejectRequest(id, userDetails.getUsername(), reason));
    }

    @GetMapping("/requests/{id}/proof")
    @Operation(summary = "Justificatif de paiement", description = "Capture Wave ou Orange Money")
    public ResponseEntity<Resource> getProof(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        Resource resource = subscriptionService.getProofResource(id, userDetails.getUsername());
        String contentType = MediaType.IMAGE_JPEG_VALUE;
        String filename = resource.getFilename();
        if (filename != null) {
            if (filename.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG_VALUE;
            } else if (filename.endsWith(".webp")) {
                contentType = "image/webp";
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"justificatif\"")
                .body(resource);
    }

    @GetMapping("/quote")
    public ResponseEntity<Map<String, Object>> quote(
            @RequestParam String planCode,
            @RequestParam String durationCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long companyId = null;
        if (userDetails != null) {
            try {
                companyId = subscriptionService.getStatusForCurrentUser(userDetails.getUsername()).getCompanyId();
            } catch (RuntimeException ignored) {
                // Super admin sans entreprise : devis sans cumul
            }
        }
        return ResponseEntity.ok(subscriptionService.buildQuote(planCode, durationCode, companyId));
    }
}
