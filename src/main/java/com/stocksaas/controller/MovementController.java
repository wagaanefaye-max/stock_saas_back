package com.stocksaas.controller;

import com.stocksaas.dto.CreateMovementRequest;
import com.stocksaas.dto.MovementDTO;
import com.stocksaas.dto.MovementTypeDTO;
import com.stocksaas.dto.PageResponse;
import com.stocksaas.model.MovementType;
import com.stocksaas.model.User;
import com.stocksaas.repository.MovementTypeRepository;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.security.SecurityAccessService;
import com.stocksaas.service.MovementService;
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
 * Controller pour la gestion des mouvements de stock
 */
@Slf4j
@RestController
@RequestMapping("/api/movements")
@RequiredArgsConstructor
@Tag(name = "Mouvements", description = "API pour la gestion des mouvements de stock")
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
public class MovementController {
    
    private final MovementService movementService;
    private final UserRepository userRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final SecurityAccessService securityAccessService;
    
    @GetMapping
    @Operation(summary = "Liste des mouvements", description = "Liste complète ou paginée (page + size), filtrable par type et recherche textuelle")
    public ResponseEntity<?> getAllMovements(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String search) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).build();
            }

            String email = authentication.getName();
            User user = userRepository.findByEmailWithCompanyAndRole(email)
                    .orElse(null);

            if (user == null || user.getCompany() == null) {
                if (page != null && size != null && size > 0) {
                    return ResponseEntity.ok(PageResponse.<MovementDTO>builder()
                            .content(List.of())
                            .page(page)
                            .size(size)
                            .totalElements(0)
                            .totalPages(0)
                            .first(true)
                            .last(true)
                            .build());
                }
                return ResponseEntity.ok(List.of());
            }

            Long companyId = user.getCompany().getId();

            List<Long> warehouseIds = null;
            if (user.getRole() != null && "GESTIONNAIRE".equals(user.getRole().getCode())) {
                warehouseIds = securityAccessService.getAssignedWarehouseIds(user);
            }

            if (page != null && size != null && size > 0) {
                PageResponse<MovementDTO> response = movementService.getMovementsPaged(
                        companyId, warehouseIds, type, search, page, size);
                return ResponseEntity.ok(response);
            }

            List<MovementDTO> movements = movementService.getMovementsByUser(
                    companyId,
                    warehouseIds,
                    type
            );

            return ResponseEntity.ok(movements);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des mouvements", e);
            return ResponseEntity.ok(List.of());
        }
    }
    
    @PostMapping
    @Operation(summary = "Créer un mouvement", description = "Crée un nouveau mouvement de stock pour l'entreprise de l'utilisateur connecté")
    public ResponseEntity<?> createMovement(@Valid @RequestBody CreateMovementRequest request) {
        try {
            // Récupérer l'utilisateur connecté
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Non authentifié"));
            }
            
            String email = authentication.getName();
            User user = userRepository.findByEmailWithCompanyAndRole(email)
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            
            if (user.getCompany() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "L'utilisateur n'a pas d'entreprise associée"));
            }
            
            Long companyId = user.getCompany().getId();
            Long userId = user.getId();
            
            MovementDTO movement = movementService.createMovement(companyId, userId, request);
            return ResponseEntity.ok(movement);
        } catch (RuntimeException e) {
            log.error("Erreur lors de la création du mouvement", e);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Erreur inattendue lors de la création du mouvement", e);
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue lors de la création du mouvement"));
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un mouvement", description = "Récupère les détails d'un mouvement par son ID")
    public ResponseEntity<?> getMovementById(@PathVariable Long id) {
        try {
            User user = securityAccessService.requireAuthenticatedUser();
            Long companyId = securityAccessService.requireCompanyId(user);
            List<Long> warehouseIds = resolveWarehouseIds(user);
            MovementDTO movement = movementService.getMovementById(companyId, warehouseIds, id);
            return ResponseEntity.ok(movement);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Une erreur est survenue"));
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un mouvement", description = "Supprime logiquement un mouvement (soft delete)")
    public ResponseEntity<Void> deleteMovement(@PathVariable Long id) {
        try {
            User user = securityAccessService.requireAuthenticatedUser();
            Long companyId = securityAccessService.requireCompanyId(user);
            List<Long> warehouseIds = resolveWarehouseIds(user);
            movementService.deleteMovement(companyId, warehouseIds, id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    private List<Long> resolveWarehouseIds(User user) {
        if (user.getRole() != null && "GESTIONNAIRE".equals(user.getRole().getCode())) {
            return securityAccessService.getAssignedWarehouseIds(user);
        }
        return null;
    }
    
    @GetMapping("/types")
    @Operation(summary = "Liste des types de mouvements", description = "Récupère la liste des types de mouvements actifs")
    public ResponseEntity<List<MovementTypeDTO>> getMovementTypes() {
        try {
            List<MovementType> types = movementTypeRepository.findByIsActiveTrue();
            List<MovementTypeDTO> typeDTOs = types.stream()
                    .map(type -> MovementTypeDTO.builder()
                            .code(type.getCode())
                            .label(type.getLabel())
                            .description(type.getDescription())
                            .allowsNegative(type.getAllowsNegative())
                            .requiresDestination(type.getRequiresDestination())
                            .isActive(type.getIsActive())
                            .build())
                    .collect(Collectors.toList());
            return ResponseEntity.ok(typeDTOs);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des types de mouvements", e);
            return ResponseEntity.ok(List.of());
        }
    }
}
