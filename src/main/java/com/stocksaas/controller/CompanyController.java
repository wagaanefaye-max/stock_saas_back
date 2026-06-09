package com.stocksaas.controller;

import com.stocksaas.dto.CompanyDTO;
import com.stocksaas.dto.CreateCompanyRequest;
import com.stocksaas.dto.PageResponse;
import com.stocksaas.dto.UpdateCompanyRequest;
import com.stocksaas.dto.UpdateCompanyStatusRequest;
import com.stocksaas.model.User;
import com.stocksaas.security.SecurityAccessService;
import com.stocksaas.service.AuthService;
import com.stocksaas.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller pour la gestion des entreprises
 */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Entreprises", description = "API pour la gestion des entreprises")
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
public class CompanyController {

    private final CompanyService companyService;
    private final AuthService authService;
    private final SecurityAccessService securityAccessService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Créer une entreprise (Super Admin)", description = "Crée une boutique et envoie un email d'activation à l'administrateur")
    public ResponseEntity<CompanyDTO> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        Long companyId = authService.createCompanyBySuperAdmin(request);
        CompanyDTO created = companyService.getCompanyById(companyId);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Liste des entreprises", description = "Récupère la liste paginée des entreprises")
    public ResponseEntity<PageResponse<CompanyDTO>> getAllCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        PageResponse<CompanyDTO> response = companyService.getAllCompanies(page, size, search);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_ENTREPRISE')")
    @Operation(summary = "Détail d'une entreprise", description = "Super-admin : toute entreprise ; admin entreprise : la sienne uniquement")
    public ResponseEntity<CompanyDTO> getCompanyById(@PathVariable Long id) {
        assertCanAccessCompany(id);
        CompanyDTO company = companyService.getCompanyById(id);
        return ResponseEntity.ok(company);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_ENTREPRISE')")
    @Operation(summary = "Mettre à jour une entreprise", description = "Super-admin : toute entreprise ; admin entreprise : paramètres de la sienne")
    public ResponseEntity<CompanyDTO> updateCompany(@PathVariable Long id, @Valid @RequestBody UpdateCompanyRequest request) {
        User user = assertCanAccessCompany(id);
        if (user.isAdminEntreprise()) {
            request.setPlanCode(null);
        }
        CompanyDTO updatedCompany = companyService.updateCompany(id, request);
        return ResponseEntity.ok(updatedCompany);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Changer le statut d'une entreprise", description = "Active ou désactive une entreprise")
    public ResponseEntity<?> updateCompanyStatus(@PathVariable Long id, @Valid @RequestBody UpdateCompanyStatusRequest request) {
        try {
            CompanyDTO updatedCompany = companyService.updateCompanyStatus(id, request);
            return ResponseEntity.ok(updatedCompany);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors du changement de statut"));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Supprimer une entreprise", description = "Supprime logiquement une entreprise (soft delete)")
    public ResponseEntity<Void> deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }

    private User assertCanAccessCompany(Long companyId) {
        User user = securityAccessService.requireAuthenticatedUser();
        if (!user.isSuperAdmin()) {
            securityAccessService.assertSameCompany(user, companyId);
        }
        return user;
    }
}
