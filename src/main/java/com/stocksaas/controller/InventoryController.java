package com.stocksaas.controller;

import com.stocksaas.dto.CreateInventoryRequest;
import com.stocksaas.dto.InventoryDTO;
import com.stocksaas.dto.UpdateInventoryLinesRequest;
import com.stocksaas.model.User;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.repository.UserWarehouseRepository;
import com.stocksaas.service.InventoryService;
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

@Slf4j
@RestController
@RequestMapping("/api/inventories")
@RequiredArgsConstructor
@Tag(name = "Inventaires", description = "API pour les sessions d'inventaire (comptage et ajustement des stocks)")
@CrossOrigin(
        origins = {
                "http://localhost:4200",
                "http://localhost:3000",
                "http://sen-stocksaas.com",
                "https://sen-stocksaas.com"
        },
        allowCredentials = "true"
)
public class InventoryController {

    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final UserWarehouseRepository userWarehouseRepository;

    @GetMapping
    @Operation(summary = "Liste des inventaires", description = "Récupère les inventaires de l'entreprise (filtrés par entrepôts si gestionnaire). Filtres optionnels : warehouseId, status.")
    public ResponseEntity<List<InventoryDTO>> getAll(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String status) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return ResponseEntity.status(401).build();
            }
            User user = userRepository.findByEmailWithCompanyAndRole(auth.getName()).orElse(null);
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.ok(List.of());
            }
            Long companyId = user.getCompany().getId();
            List<Long> warehouseIds = null;
            if (user.getRole() != null && "GESTIONNAIRE".equals(user.getRole().getCode())) {
                warehouseIds = userWarehouseRepository.findAll().stream()
                        .filter(uw -> uw.getUser() != null && uw.getUser().getId().equals(user.getId()))
                        .filter(uw -> uw.getWarehouse() != null && uw.getWarehouse().getId() != null)
                        .map(uw -> uw.getWarehouse().getId())
                        .collect(Collectors.toList());
            }
            List<InventoryDTO> list = inventoryService.listByCompany(companyId, warehouseIds, warehouseId, status);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("Erreur liste inventaires", e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un inventaire", description = "Récupère un inventaire avec ses lignes (produits, théorique, compté, écart)")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
            }
            InventoryDTO dto = inventoryService.getById(user.getCompany().getId(), id);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur détail inventaire", e);
            return ResponseEntity.status(500).body(Map.of("message", "Erreur serveur"));
        }
    }

    @PostMapping
    @Operation(summary = "Créer un inventaire", description = "Crée une session d'inventaire pour un entrepôt et initialise les lignes à partir des stocks actuels")
    public ResponseEntity<?> create(@Valid @RequestBody CreateInventoryRequest request) {
        try {
            User user = getCurrentUser();
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
            }
            InventoryDTO dto = inventoryService.create(user.getCompany().getId(), user.getId(), request);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur création inventaire", e);
            return ResponseEntity.status(500).body(Map.of("message", "Erreur serveur"));
        }
    }

    @PatchMapping("/{id}/lines")
    @Operation(summary = "Mettre à jour les quantités comptées", description = "Enregistre les quantités comptées par produit (inventaire en cours uniquement)")
    public ResponseEntity<?> updateLines(@PathVariable Long id, @Valid @RequestBody UpdateInventoryLinesRequest request) {
        try {
            User user = getCurrentUser();
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
            }
            InventoryDTO dto = inventoryService.updateLines(user.getCompany().getId(), id, request);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur mise à jour lignes inventaire", e);
            return ResponseEntity.status(500).body(Map.of("message", "Erreur serveur"));
        }
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Clôturer l'inventaire", description = "Génère les mouvements d'ajustement pour les écarts et clôture la session")
    public ResponseEntity<?> close(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null || user.getCompany() == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
            }
            InventoryDTO dto = inventoryService.close(user.getCompany().getId(), user.getId(), id);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur clôture inventaire", e);
            return ResponseEntity.status(500).body(Map.of("message", "Erreur serveur"));
        }
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmailWithCompanyAndRole(auth.getName()).orElse(null);
    }
}
