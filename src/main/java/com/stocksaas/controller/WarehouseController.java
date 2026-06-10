package com.stocksaas.controller;

import com.stocksaas.dto.CreateWarehouseRequest;
import com.stocksaas.dto.ProductInWarehouseDTO;
import com.stocksaas.dto.UpdateStockThresholdRequest;
import com.stocksaas.dto.UpdateWarehouseRequest;
import com.stocksaas.dto.WarehouseDTO;
import com.stocksaas.dto.WarehouseDTOForCreatingProduct;
import com.stocksaas.model.User;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.repository.UserWarehouseRepository;
import com.stocksaas.security.SecurityAccessService;
import com.stocksaas.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller pour la gestion des entrepôts
 */
@Slf4j
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Tag(name = "Entrepôts", description = "API pour la gestion des entrepôts")
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
public class WarehouseController {
    
    private final WarehouseService warehouseService;
    private final UserRepository userRepository;
    private final UserWarehouseRepository userWarehouseRepository;
    private final SecurityAccessService securityAccessService;
    
    @GetMapping
    @Operation(summary = "Liste des entrepôts", description = "Récupère la liste complète des entrepôts avec tous les détails")
    public ResponseEntity<List<WarehouseDTO>> getAllWarehouses() {
        try {
            // Récupérer l'utilisateur connecté
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).build();
            }
            
            String email = authentication.getName();
            User user = userRepository.findByEmailWithCompanyAndRole(email)
                    .orElse(null);
            
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.ok(List.of());
            }
            
            Long companyId = user.getCompany().getId();
            
            // Récupérer les entrepôts assignés à l'utilisateur (si gestionnaire)
            List<Long> warehouseIds = null;
            if (user.getRole() != null && "GESTIONNAIRE".equals(user.getRole().getCode())) {
                warehouseIds = userWarehouseRepository.findAll().stream()
                        .filter(uw -> uw.getUser() != null && uw.getUser().getId() != null && 
                                   uw.getUser().getId().equals(user.getId()))
                        .filter(uw -> uw.getWarehouse() != null && uw.getWarehouse().getId() != null)
                        .map(uw -> uw.getWarehouse().getId())
                        .collect(Collectors.toList());
            }
            
            List<WarehouseDTO> warehouses = warehouseService.getWarehousesByUser(
                    companyId,
                    warehouseIds
            );
            
            return ResponseEntity.ok(warehouses);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des entrepôts", e);
            return ResponseEntity.ok(List.of());
        }
    }
    
    @GetMapping("/simple")
    @Operation(summary = "Liste simplifiée des entrepôts", description = "Récupère la liste simplifiée des entrepôts (id et name uniquement) pour la création de produits")
    public ResponseEntity<List<WarehouseDTOForCreatingProduct>> getSimpleWarehouses() {
        try {
            // Récupérer l'utilisateur connecté
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).build();
            }
            
            String email = authentication.getName();
            User user = userRepository.findByEmailWithCompanyAndRole(email)
                    .orElse(null);
            
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.ok(List.of());
            }
            
            Long companyId = user.getCompany().getId();
            
            // Récupérer les entrepôts assignés à l'utilisateur (si gestionnaire)
            List<Long> warehouseIds = null;
            if (user.getRole() != null && "GESTIONNAIRE".equals(user.getRole().getCode())) {
                warehouseIds = userWarehouseRepository.findAll().stream()
                        .filter(uw -> uw.getUser() != null && uw.getUser().getId() != null && 
                                   uw.getUser().getId().equals(user.getId()))
                        .filter(uw -> uw.getWarehouse() != null && uw.getWarehouse().getId() != null)
                        .map(uw -> uw.getWarehouse().getId())
                        .toList();
            }
            
            List<WarehouseDTOForCreatingProduct> warehouses = warehouseService.getSimpleWarehousesByUser(
                    companyId,
                    warehouseIds
            );
            
            return ResponseEntity.ok(warehouses);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des entrepôts simplifiés", e);
            return ResponseEntity.ok(List.of());
        }
    }
    
    @PostMapping
    @Operation(summary = "Créer un entrepôt", description = "Crée un nouvel entrepôt pour l'entreprise de l'utilisateur connecté")
    public ResponseEntity<?> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        try {
            User user = securityAccessService.requireAdminEntreprise();
            Long companyId = securityAccessService.requireCompanyId(user);
            WarehouseDTO warehouse = warehouseService.createWarehouse(companyId, request);
            return ResponseEntity.ok(warehouse);
        } catch (RuntimeException e) {
            log.error("Erreur lors de la création de l'entrepôt", e);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur inattendue lors de la création de l'entrepôt", e);
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la création de l'entrepôt"));
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un entrepôt", description = "Récupère les détails d'un entrepôt par son ID")
    public ResponseEntity<?> getWarehouseById(@PathVariable Long id) {
        try {
            User user = securityAccessService.requireAuthenticatedUser();
            Long companyId = securityAccessService.requireCompanyId(user);
            securityAccessService.assertWarehouseAccessible(user, id);
            WarehouseDTO warehouse = warehouseService.getWarehouseById(companyId, id);
            return ResponseEntity.ok(warehouse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue"));
        }
    }
    
    @GetMapping("/{id}/products")
    @Operation(summary = "Produits dans l'entrepôt", description = "Récupère la liste des produits présents dans l'entrepôt avec leur quantité")
    public ResponseEntity<?> getProductsInWarehouse(@PathVariable Long id) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
            }
            
            String email = authentication.getName();
            User user = userRepository.findByEmailWithCompanyAndRole(email)
                    .orElse(null);
            
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.ok(List.of());
            }
            
            Long companyId = user.getCompany().getId();
            
            // Récupérer les entrepôts accessibles par l'utilisateur
            List<Long> warehouseIds = null;
            if (user.getRole() != null && "GESTIONNAIRE".equals(user.getRole().getCode())) {
                warehouseIds = userWarehouseRepository.findAll().stream()
                        .filter(uw -> uw.getUser() != null && uw.getUser().getId().equals(user.getId()))
                        .filter(uw -> uw.getWarehouse() != null)
                        .map(uw -> uw.getWarehouse().getId())
                        .collect(Collectors.toList());
            }
            List<WarehouseDTO> userWarehouses = warehouseService.getWarehousesByUser(companyId, warehouseIds);
            
            // Vérifier que l'entrepôt demandé est accessible
            boolean hasAccess = userWarehouses.stream().anyMatch(w -> w.getId().equals(id));
            if (!hasAccess) {
                return ResponseEntity.status(403).body(Map.of("message", "Accès non autorisé à cet entrepôt"));
            }
            
            List<ProductInWarehouseDTO> products = warehouseService.getProductsInWarehouse(id);
            return ResponseEntity.ok(products);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des produits de l'entrepôt", e);
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue"));
        }
    }
    
    @PutMapping("/{warehouseId}/products/{productId}/threshold")
    @Operation(summary = "Seuil minimum produit", description = "Met à jour le seuil minimum d'un produit dans un entrepôt")
    public ResponseEntity<?> updateProductMinThreshold(
            @PathVariable Long warehouseId,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateStockThresholdRequest request) {
        try {
            User user = securityAccessService.requireAdminEntreprise();
            Long companyId = securityAccessService.requireCompanyId(user);
            securityAccessService.assertWarehouseAccessible(user, warehouseId);
            ProductInWarehouseDTO updated = warehouseService.updateProductMinThreshold(
                    companyId, warehouseId, productId, request);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour du seuil minimum", e);
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue"));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour un entrepôt", description = "Met à jour les informations d'un entrepôt existant")
    public ResponseEntity<?> updateWarehouse(@PathVariable Long id, @Valid @RequestBody UpdateWarehouseRequest request) {
        try {
            User user = securityAccessService.requireAdminEntreprise();
            Long companyId = securityAccessService.requireCompanyId(user);
            WarehouseDTO warehouse = warehouseService.updateWarehouse(companyId, id, request);
            return ResponseEntity.ok(warehouse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la mise à jour"));
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un entrepôt", description = "Supprime logiquement un entrepôt (soft delete)")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        try {
            User user = securityAccessService.requireAdminEntreprise();
            Long companyId = securityAccessService.requireCompanyId(user);
            warehouseService.deleteWarehouse(companyId, id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
