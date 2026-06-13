package com.stocksaas.service;

import com.stocksaas.exception.ForbiddenAccessException;
import com.stocksaas.dto.CreateMovementRequest;
import com.stocksaas.dto.MovementDTO;
import com.stocksaas.dto.PageResponse;
import com.stocksaas.model.*;
import com.stocksaas.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service pour la gestion des mouvements de stock
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovementService {
    
    private final MovementRepository movementRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductStatusRepository productStatusRepository;
    
    /**
     * Crée un nouveau mouvement de stock
     */
    @Transactional
    public MovementDTO createMovement(Long companyId, Long userId, CreateMovementRequest request) {
        // Vérifier que l'entreprise existe
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + companyId));
        
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }
        
        // Vérifier que le produit existe et appartient à l'entreprise
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Produit non trouvé avec l'ID: " + request.getProductId()));
        
        if (!product.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Le produit n'appartient pas à cette entreprise");
        }
        
        if (product.getIsDeleted()) {
            throw new RuntimeException("Le produit a été supprimé");
        }
        
        // Vérifier que l'utilisateur existe
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé avec l'ID: " + userId));
        
        // Code type en majuscules (ENTREE, SORTIE, TRANSFERT, AJUSTEMENT)
        String typeCode = request.getTypeCode() != null ? request.getTypeCode().toUpperCase() : null;
        MovementType movementType = movementTypeRepository.findById(typeCode)
                .or(() -> movementTypeRepository.findById(legacyCodeFromUpper(typeCode)))
                .orElseThrow(() -> new RuntimeException("Type de mouvement invalide: " + request.getTypeCode()));
        
        if (!movementType.getIsActive()) {
            throw new RuntimeException("Le type de mouvement n'est pas actif");
        }
        
        String internalTypeCode = normalizeTypeCode(movementType.getCode());

        // Déterminer l'entrepôt source
        Warehouse warehouse;
        if ("TRANSFERT".equals(internalTypeCode) && request.getWarehouseId() == null) {
            // Trouver automatiquement l'entrepôt où le produit a du stock
            var levels = stockLevelRepository.findActiveByProductIdWithWarehouse(product.getId());
            var withStock = levels.stream()
                    .filter(sl -> sl.getQuantity() != null && sl.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            if (withStock.isEmpty()) {
                throw new RuntimeException(
                        "Aucun stock enregistré pour ce produit. Effectuez d'abord une entrée de stock avant un transfert.");
            }
            // Prendre l'entrepôt avec le plus de stock
            StockLevel best = withStock.stream()
                    .max((a, b) -> a.getQuantity().compareTo(b.getQuantity()))
                    .orElse(withStock.get(0));
            warehouse = best.getWarehouse();
        } else {
            if (request.getWarehouseId() == null) {
                throw new RuntimeException("L'entrepôt est obligatoire pour ce type de mouvement");
            }
            warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new RuntimeException("Entrepôt non trouvé avec l'ID: " + request.getWarehouseId()));
        }

        if (!warehouse.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("L'entrepôt n'appartient pas à cette entreprise");
        }
        
        if (warehouse.getIsDeleted()) {
            throw new RuntimeException("L'entrepôt a été supprimé");
        }
        
        // Validation spécifique selon le type
        if ("TRANSFERT".equals(internalTypeCode)) {
            if (request.getDestinationWarehouseId() == null) {
                throw new RuntimeException("L'entrepôt de destination est obligatoire pour un transfert");
            }
            
            if (request.getDestinationWarehouseId().equals(warehouse.getId())) {
                throw new RuntimeException("L'entrepôt source et l'entrepôt de destination doivent être différents");
            }
            
            Warehouse destinationWarehouse = warehouseRepository.findById(request.getDestinationWarehouseId())
                    .orElseThrow(() -> new RuntimeException("Entrepôt de destination non trouvé avec l'ID: " + request.getDestinationWarehouseId()));
            
            if (!destinationWarehouse.getCompany().getId().equals(companyId)) {
                throw new RuntimeException("L'entrepôt de destination n'appartient pas à cette entreprise");
            }
            
            if (destinationWarehouse.getIsDeleted()) {
                throw new RuntimeException("L'entrepôt de destination a été supprimé");
            }
        } else {
            if (request.getDestinationWarehouseId() != null) {
                throw new RuntimeException("L'entrepôt de destination ne doit être spécifié que pour les transferts");
            }
        }
        
        // Validation de la quantité
        if (!"AJUSTEMENT".equals(internalTypeCode) && request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("La quantité doit être positive pour ce type de mouvement");
        }
        
        // Vérifier le stock disponible pour les sorties et transferts
        if ("SORTIE".equals(internalTypeCode) || "TRANSFERT".equals(internalTypeCode)) {
            StockLevel stockLevel = stockLevelRepository.findActiveByProductIdAndWarehouseId(
                    request.getProductId(), warehouse.getId())
                    .orElse(null);
            
            BigDecimal availableStock = stockLevel != null ? stockLevel.getQuantity() : BigDecimal.ZERO;
            
            if (availableStock.compareTo(request.getQuantity()) < 0) {
                throw new RuntimeException("Stock insuffisant. Disponible : " + availableStock + ", demandé : " + request.getQuantity());
            }
        }
        
        // Créer le mouvement
        Movement movement = new Movement();
        movement.setCompany(company);
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setUser(user);
        movement.setType(movementType);
        movement.setQuantity(request.getQuantity());
        movement.setDate(request.getDate());
        movement.setJustification(request.getJustification());
        
        if ("TRANSFERT".equals(internalTypeCode)) {
            // Vérifier que l'entrepôt de destination est différent de l'entrepôt d'origine
            if (request.getDestinationWarehouseId().equals(warehouse.getId())) {
                throw new RuntimeException("L'entrepôt d'origine et l'entrepôt de destination doivent être différents");
            }
            
            Warehouse destinationWarehouse = warehouseRepository.findById(request.getDestinationWarehouseId())
                    .orElseThrow(() -> new RuntimeException("Entrepôt de destination non trouvé"));
            movement.setDestinationWarehouse(destinationWarehouse);
        }
        
        // Sauvegarder le mouvement (le trigger SQL mettra à jour les stocks automatiquement)
        Movement savedMovement = movementRepository.save(movement);
        
        // Mettre à jour manuellement les niveaux de stock pour plus de contrôle
        updateStockLevels(savedMovement);
        
        return mapToDTO(savedMovement);
    }
    
    /** Ancien code (base déjà peuplée avec Entrée, Sortie, etc.) pour compatibilité. */
    private static String legacyCodeFromUpper(String upper) {
        if (upper == null) return null;
        return switch (upper) {
            case "ENTREE" -> "Entrée";
            case "SORTIE" -> "Sortie";
            case "TRANSFERT" -> "Transfert";
            case "AJUSTEMENT" -> "Ajustement";
            default -> upper;
        };
    }

    /** Normalise le code type (ENTREE, SORTIE, TRANSFERT, AJUSTEMENT) pour la logique. */
    private static String normalizeTypeCode(String code) {
        if (code == null) return null;
        String u = code.toUpperCase();
        if ("ENTREE".equals(u) || "ENTRÉE".equals(u)) return "ENTREE";
        if ("SORTIE".equals(u)) return "SORTIE";
        if ("TRANSFERT".equals(u)) return "TRANSFERT";
        if ("AJUSTEMENT".equals(u)) return "AJUSTEMENT";
        return u;
    }

    /**
     * Met à jour les niveaux de stock selon le mouvement
     */
    private void updateStockLevels(Movement movement) {
        String internalTypeCode = normalizeTypeCode(movement.getType().getCode());
        BigDecimal quantity = movement.getQuantity();
        
        if ("ENTREE".equals(internalTypeCode)) {
            // Augmenter le stock de l'entrepôt
            StockLevel updatedStockLevel = updateStockLevel(movement.getProduct(), movement.getWarehouse(), quantity, true);
            // Mettre à jour le statut du produit si nécessaire
            updateProductStatus(movement.getProduct(), updatedStockLevel);
            
        } else if ("SORTIE".equals(internalTypeCode)) {
            // Diminuer le stock de l'entrepôt
            StockLevel updatedStockLevel = updateStockLevel(movement.getProduct(), movement.getWarehouse(), quantity, false);
            // Mettre à jour le statut du produit si nécessaire
            updateProductStatus(movement.getProduct(), updatedStockLevel);
            
        } else if ("TRANSFERT".equals(internalTypeCode)) {
            // Diminuer le stock de l'entrepôt source
            StockLevel sourceStockLevel = updateStockLevel(movement.getProduct(), movement.getWarehouse(), quantity, false);
            // Augmenter le stock de l'entrepôt destination
            StockLevel destStockLevel = updateStockLevel(movement.getProduct(), movement.getDestinationWarehouse(), quantity, true);
            // Mettre à jour le statut du produit basé sur le stock source (entrepôt principal)
            updateProductStatus(movement.getProduct(), sourceStockLevel);
            
        } else if ("AJUSTEMENT".equals(internalTypeCode)) {
            // Ajuster le stock (peut être positif ou négatif)
            StockLevel updatedStockLevel;
            if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                updatedStockLevel = updateStockLevel(movement.getProduct(), movement.getWarehouse(), quantity, true);
            } else {
                updatedStockLevel = updateStockLevel(movement.getProduct(), movement.getWarehouse(), quantity.abs(), false);
            }
            // Mettre à jour le statut du produit si nécessaire
            updateProductStatus(movement.getProduct(), updatedStockLevel);
        }
    }
    
    /**
     * Met à jour le statut du produit selon le niveau de stock
     */
    private void updateProductStatus(Product product, StockLevel stockLevel) {
        if (stockLevel == null) {
            return;
        }
        
        // Déterminer le nouveau statut basé sur le stock
        String statusCode = stockLevel.getQuantity().compareTo(BigDecimal.ZERO) > 0 ? "En stock" : "Rupture";
        
        ProductStatus status = productStatusRepository.findById(statusCode)
                .orElseGet(() -> {
                    // Créer le statut s'il n'existe pas
                    ProductStatus newStatus = new ProductStatus();
                    newStatus.setCode(statusCode);
                    newStatus.setLabel(statusCode);
                    newStatus.setIsActive(true);
                    return productStatusRepository.save(newStatus);
                });
        
        product.setStatus(status);
        productRepository.save(product);
    }
    
    /**
     * Met à jour un niveau de stock
     * @return Le StockLevel mis à jour
     */
    private StockLevel updateStockLevel(Product product, Warehouse warehouse, BigDecimal quantity, boolean add) {
        StockLevel stockLevel = stockLevelRepository.findByProductIdAndWarehouseId(
                product.getId(), warehouse.getId())
                .orElse(null);
        
        if (stockLevel == null) {
            stockLevel = new StockLevel();
            stockLevel.setProduct(product);
            stockLevel.setWarehouse(warehouse);
            stockLevel.setQuantity(BigDecimal.ZERO);
        }
        
        if (add) {
            stockLevel.setQuantity(stockLevel.getQuantity().add(quantity));
        } else {
            BigDecimal newQuantity = stockLevel.getQuantity().subtract(quantity);
            stockLevel.setQuantity(newQuantity.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newQuantity);
        }
        
        return stockLevelRepository.save(stockLevel);
    }
    
    /**
     * Récupère les mouvements paginés avec filtres optionnels.
     */
    @Transactional(readOnly = true)
    public PageResponse<MovementDTO> getMovementsPaged(Long companyId, List<Long> warehouseIds,
            String typeCode, String search, int page, int size) {
        Specification<Movement> spec = buildMovementSpecification(companyId, warehouseIds, typeCode, search);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Movement> movementPage = movementRepository.findAll(spec, pageable);
        return PageResponse.<MovementDTO>builder()
                .content(movementPage.getContent().stream().map(this::mapToDTO).collect(Collectors.toList()))
                .page(movementPage.getNumber())
                .size(movementPage.getSize())
                .totalElements(movementPage.getTotalElements())
                .totalPages(movementPage.getTotalPages())
                .first(movementPage.isFirst())
                .last(movementPage.isLast())
                .build();
    }

    /**
     * Récupère tous les mouvements d'une entreprise, filtrés par entrepôts et type si nécessaire
     */
    @Transactional(readOnly = true)
    public List<MovementDTO> getMovementsByUser(Long companyId, List<Long> warehouseIds, String typeCode) {
        List<Movement> movements;
        
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            // Filtrer par entrepôts assignés
            movements = movementRepository.findByCompanyIdAndWarehouseIds(companyId, warehouseIds);
        } else {
            // Tous les mouvements de l'entreprise
            movements = movementRepository.findByCompanyId(companyId);
        }
        
        // Filtre par type en mémoire (backend), en normalisant les codes (ENTREE/Entrée, etc.)
        String normalizedType = (typeCode != null && !"ALL".equalsIgnoreCase(typeCode))
                ? normalizeTypeCode(typeCode.trim())
                : null;
        if (normalizedType != null) {
            movements = movements.stream()
                    .filter(m -> normalizedType.equals(normalizeTypeCode(m.getType().getCode())))
                    .toList();
        }
        
        return movements.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private Specification<Movement> buildMovementSpecification(Long companyId, List<Long> warehouseIds,
            String typeCode, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            predicates.add(cb.equal(root.get("isDeleted"), false));

            if (warehouseIds != null && !warehouseIds.isEmpty()) {
                predicates.add(root.get("warehouse").get("id").in(warehouseIds));
            }

            if (typeCode != null && !typeCode.isBlank() && !"ALL".equalsIgnoreCase(typeCode.trim())) {
                String dbTypeCode = toDatabaseTypeCode(normalizeTypeCode(typeCode.trim()));
                if (dbTypeCode != null) {
                    predicates.add(cb.equal(root.get("type").get("code"), dbTypeCode));
                }
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("product").get("name")), pattern),
                        cb.like(cb.lower(root.get("warehouse").get("name")), pattern),
                        cb.like(cb.lower(root.get("user").get("name")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("justification"), "")), pattern)
                ));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String toDatabaseTypeCode(String normalized) {
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "ENTREE" -> "Entrée";
            case "SORTIE" -> "Sortie";
            case "TRANSFERT" -> "Transfert";
            case "AJUSTEMENT" -> "Ajustement";
            default -> normalized;
        };
    }
    
    /**
     * Récupère un mouvement par son ID
     */
    @Transactional(readOnly = true)
    public MovementDTO getMovementById(Long companyId, List<Long> warehouseIds, Long id) {
        Movement movement = movementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mouvement non trouvé avec l'ID: " + id));
        assertMovementAccess(movement, companyId, warehouseIds);
        return mapToDTO(movement);
    }
    
    /**
     * Supprime logiquement un mouvement
     */
    @Transactional
    public void deleteMovement(Long companyId, List<Long> warehouseIds, Long id) {
        Movement movement = movementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mouvement non trouvé avec l'ID: " + id));
        assertMovementAccess(movement, companyId, warehouseIds);
        movement.setIsDeleted(true);
        movementRepository.save(movement);
    }

    private void assertMovementAccess(Movement movement, Long companyId, List<Long> warehouseIds) {
        if (movement.getIsDeleted()) {
            throw new RuntimeException("Mouvement non trouvé avec l'ID: " + movement.getId());
        }
        if (movement.getCompany() == null || !companyId.equals(movement.getCompany().getId())) {
            throw new ForbiddenAccessException("Accès non autorisé à ce mouvement");
        }
        if (warehouseIds != null && !warehouseIds.isEmpty()
                && (movement.getWarehouse() == null || !warehouseIds.contains(movement.getWarehouse().getId()))) {
            throw new ForbiddenAccessException("Accès non autorisé à ce mouvement");
        }
    }
    
    /**
     * Convertit une entité Movement en DTO
     */
    private MovementDTO mapToDTO(Movement movement) {
        String typeCode = movement.getType().getCode();
        
        return MovementDTO.builder()
                .id(movement.getId())
                .productId(movement.getProduct().getId())
                .productName(movement.getProduct().getName())
                .productSku(movement.getProduct().getSku())
                .typeCode(typeCode)
                .typeLabel(movement.getType().getLabel())
                .quantity(movement.getQuantity())
                .date(movement.getDate())
                .warehouseId(movement.getWarehouse().getId())
                .warehouseName(movement.getWarehouse().getName())
                .destinationWarehouseId(movement.getDestinationWarehouse() != null ? movement.getDestinationWarehouse().getId() : null)
                .destinationWarehouseName(movement.getDestinationWarehouse() != null ? movement.getDestinationWarehouse().getName() : null)
                .userId(movement.getUser().getId())
                .userName(movement.getUser().getName())
                .justification(movement.getJustification())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
