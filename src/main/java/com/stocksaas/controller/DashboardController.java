package com.stocksaas.controller;

import com.stocksaas.dto.DashboardStatsDTO;
import com.stocksaas.dto.SuperAdminDashboardStatsDTO;
import com.stocksaas.model.User;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.repository.UserWarehouseRepository;
import com.stocksaas.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller pour les statistiques du dashboard
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "API pour les statistiques du dashboard")
@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://sen-stocksaas.com",
                "https://sen-stocksaas.com",
                "http://164.132.43.247",
                "http://164.132.43.247:4200"
        }
)
public class DashboardController {
    
    private final DashboardService dashboardService;
    private final UserRepository userRepository;
    private final UserWarehouseRepository userWarehouseRepository;
    
    @GetMapping("/stats")
    @Operation(summary = "Statistiques du dashboard", description = "Récupère les statistiques du dashboard pour l'utilisateur connecté")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        try {
            // Récupérer l'utilisateur connecté depuis le contexte de sécurité
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                log.warn("Authentification non trouvée");
                return ResponseEntity.status(401).build();
            }
            
            String email = authentication.getName();
            log.debug("Récupération des statistiques pour l'utilisateur: {}", email);
            
            User user = userRepository.findByEmailWithCompanyAndRole(email)
                    .orElse(null);
            
            if (user == null) {
                log.warn("Utilisateur non trouvé pour l'email: {}", email);
                return ResponseEntity.status(401).build();
            }
            
            // Si l'utilisateur est SUPER_ADMIN, il n'a pas d'entreprise
            if (user.isSuperAdmin()) {
                log.debug("Utilisateur SUPER_ADMIN - retour de données vides");
                DashboardStatsDTO emptyStats = emptyDashboardStats();
                return ResponseEntity.ok(emptyStats);
            }
            
            Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
            if (companyId == null) {
                log.warn("L'utilisateur {} n'a pas d'entreprise associée", email);
                return ResponseEntity.ok(emptyDashboardStats());
            }
            
            // Récupérer les entrepôts assignés à l'utilisateur (si gestionnaire)
            List<Long> warehouseIds = null;
            try {
                if (user.getRole() != null && "GESTIONNAIRE".equals(user.getRole().getCode())) {
                    warehouseIds = userWarehouseRepository.findAll().stream()
                            .filter(uw -> uw.getUser() != null && uw.getUser().getId() != null && 
                                       uw.getUser().getId().equals(user.getId()))
                            .filter(uw -> uw.getWarehouse() != null && uw.getWarehouse().getId() != null)
                            .map(uw -> uw.getWarehouse().getId())
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("Erreur lors de la récupération des entrepôts assignés", e);
            }
            
            DashboardStatsDTO stats = dashboardService.getDashboardStats(user.getId(), companyId, warehouseIds);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des statistiques du dashboard", e);
            // Retourner un DTO vide plutôt qu'une erreur 500
            try {
                return ResponseEntity.ok(emptyDashboardStats());
            } catch (Exception ex) {
                log.error("Erreur lors de la création du DTO vide", ex);
                return ResponseEntity.status(500).build();
            }
        }
    }

    private static DashboardStatsDTO emptyDashboardStats() {
        return DashboardStatsDTO.builder()
                .totalProducts(0L)
                .totalWarehouses(0L)
                .monthlyMovements(0L)
                .alerts(0L)
                .activeUsers(0L)
                .monthlyMovementsData(Collections.emptyList())
                .productsByCategory(Collections.emptyList())
                .recentMovements(Collections.emptyList())
                .paidRevenue(BigDecimal.ZERO)
                .pendingRevenue(BigDecimal.ZERO)
                .paidInvoicesCount(0L)
                .draftInvoicesCount(0L)
                .sentInvoicesCount(0L)
                .cancelledInvoicesCount(0L)
                .salesByMonth(Collections.emptyList())
                .pendingInvoices(Collections.emptyList())
                .recentInvoices(Collections.emptyList())
                .lowStockItems(Collections.emptyList())
                .productsChange("0%")
                .warehousesChange("0")
                .movementsChange("0%")
                .alertsChange("0")
                .build();
    }
    
    @GetMapping("/super-admin/stats")
    @Operation(summary = "Statistiques du dashboard Super Admin", description = "Récupère les statistiques du dashboard pour le Super Admin")
    public ResponseEntity<SuperAdminDashboardStatsDTO> getSuperAdminDashboardStats() {
        try {
            SuperAdminDashboardStatsDTO stats = dashboardService.getSuperAdminDashboardStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des statistiques Super Admin", e);
            e.printStackTrace(); // Log l'erreur pour le débogage
            // Retourner un DTO vide plutôt qu'une erreur 500 pour éviter de casser le frontend
            try {
                SuperAdminDashboardStatsDTO emptyStats = SuperAdminDashboardStatsDTO.builder()
                        .activeCompanies(0L)
                        .totalUsers(0L)
                        .monthlyRevenue("0 FCFA")
                        .supportTickets(0L)
                        .monthlyCompaniesData(java.util.Collections.emptyList())
                        .monthlySubscriptionsData(java.util.Collections.emptyList())
                        .planDistribution(java.util.Collections.emptyList())
                        .recentCompanies(java.util.Collections.emptyList())
                        .companiesChange("0")
                        .usersChange("0%")
                        .revenueChange("0%")
                        .ticketsChange("0")
                        .build();
                return ResponseEntity.ok(emptyStats);
            } catch (Exception ex) {
                log.error("Erreur lors de la création du DTO vide", ex);
                return ResponseEntity.status(500).build();
            }
        }
    }
}
