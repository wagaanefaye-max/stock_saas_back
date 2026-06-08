package com.stocksaas.service;

import com.stocksaas.exception.ForbiddenAccessException;
import com.stocksaas.dto.CreateWarehouseRequest;
import com.stocksaas.dto.ProductInWarehouseDTO;
import com.stocksaas.dto.UpdateWarehouseRequest;
import com.stocksaas.dto.WarehouseDTO;
import com.stocksaas.dto.WarehouseDTOForCreatingProduct;
import com.stocksaas.model.Company;
import com.stocksaas.model.StockLevel;
import com.stocksaas.model.Warehouse;
import com.stocksaas.model.WarehouseStatus;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.StockLevelRepository;
import com.stocksaas.repository.WarehouseRepository;
import com.stocksaas.repository.WarehouseStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocksaas.util.ProductReferenceUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service pour la gestion des entrepôts
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {
    
    private final WarehouseRepository warehouseRepository;
    private final CompanyRepository companyRepository;
    private final WarehouseStatusRepository warehouseStatusRepository;
    private final StockLevelRepository stockLevelRepository;
    
    /**
     * Récupère tous les entrepôts d'une entreprise (complets)
     */
    @Transactional(readOnly = true)
    public List<WarehouseDTO> getAllWarehousesByCompany(Long companyId) {
        return warehouseRepository.findByCompanyIdAndNotDeleted(companyId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Récupère les entrepôts assignés à un utilisateur (si gestionnaire)
     */
    @Transactional(readOnly = true)
    public List<WarehouseDTO> getWarehousesByUser(Long companyId, List<Long> warehouseIds) {
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            // Filtrer par entrepôts assignés
            return warehouseRepository.findByCompanyIdAndIdsAndNotDeleted(companyId, warehouseIds).stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        } else {
            // Tous les entrepôts de l'entreprise
            return getAllWarehousesByCompany(companyId);
        }
    }
    
    /**
     * Récupère les entrepôts simplifiés (id et name uniquement) pour un utilisateur
     */
    @Transactional(readOnly = true)
    public List<WarehouseDTOForCreatingProduct> getSimpleWarehousesByUser(Long companyId, List<Long> warehouseIds) {
        List<Warehouse> warehouses;
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            // Filtrer par entrepôts assignés
            warehouses = warehouseRepository.findByCompanyIdAndIdsAndNotDeleted(companyId, warehouseIds);
        } else {
            // Tous les entrepôts de l'entreprise
            warehouses = warehouseRepository.findByCompanyIdAndNotDeleted(companyId);
        }
        
        return warehouses.stream()
                .map(w -> new WarehouseDTOForCreatingProduct(w.getId(), w.getName()))
                .collect(Collectors.toList());
    }
    
    /**
     * Crée un nouvel entrepôt
     */
    @Transactional
    public WarehouseDTO createWarehouse(Long companyId, CreateWarehouseRequest request) {
        // Vérifier que l'entreprise existe
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + companyId));
        
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }
        
        // Créer l'entrepôt
        Warehouse warehouse = new Warehouse();
        warehouse.setCompany(company);
        warehouse.setName(request.getName());
        warehouse.setRegion(request.getRegion());
        warehouse.setDescription(request.getDescription());

        // Définir le statut (code normalisé pour tp_warehouse_status : Actif, Inactif, Maintenance)
        String statusCode = request.getStatusCode() != null ? request.getStatusCode() : "Actif";
        final String normalizedCode = statusCode == null ? "Actif"
                : "INACTIF".equals(statusCode.toUpperCase()) ? "Inactif"
                : "MAINTENANCE".equals(statusCode.toUpperCase()) ? "Maintenance" : "Actif";
        WarehouseStatus status = warehouseStatusRepository.findById(normalizedCode)
                .orElseGet(() -> {
                    WarehouseStatus newStatus = new WarehouseStatus();
                    newStatus.setCode(normalizedCode);
                    newStatus.setLabel(normalizedCode);
                    newStatus.setIsActive(true);
                    return warehouseStatusRepository.save(newStatus);
                });
        warehouse.setStatus(status);
        
        return mapToDTO(warehouseRepository.save(warehouse));
    }
    
    /**
     * Récupère la liste des produits présents dans un entrepôt avec leur quantité
     */
    @Transactional(readOnly = true)
    public List<ProductInWarehouseDTO> getProductsInWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Entrepôt non trouvé avec l'ID: " + warehouseId));
        
        if (warehouse.getIsDeleted()) {
            throw new RuntimeException("L'entrepôt a été supprimé");
        }
        
        List<StockLevel> levels = stockLevelRepository.findByWarehouseIdWithProduct(warehouseId);
        
        return levels.stream()
                .filter(sl -> sl.getProduct() != null && !sl.getProduct().getIsDeleted())
                .map(sl -> {
                    var p = sl.getProduct();
                    String ref = p.getReference();
                    if (ref == null && p.getId() != null && p.getCreatedAt() != null) {
                        ref = p.getSku() != null
                                ? ProductReferenceUtil.buildReference(p.getCreatedAt().toLocalDate(), p.getSku(), p.getId())
                                : ProductReferenceUtil.buildFallbackReference(p.getCreatedAt().toLocalDate(), p.getId());
                    }
                    return ProductInWarehouseDTO.builder()
                        .productId(p.getId())
                        .productReference(ref)
                        .productName(p.getName())
                        .sku(p.getSku())
                        .category(sl.getProduct().getProductCategory() != null ? sl.getProduct().getProductCategory().getLabel() : null)
                        .price(sl.getProduct().getPrice() != null ? sl.getProduct().getPrice() : BigDecimal.ZERO)
                        .quantity(sl.getQuantity() != null ? sl.getQuantity() : BigDecimal.ZERO)
                        .minThreshold(sl.getMinThreshold())
                        .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Récupère un entrepôt par son ID
     */
    @Transactional(readOnly = true)
    public WarehouseDTO getWarehouseById(Long companyId, Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entrepôt non trouvé avec l'ID: " + id));
        
        if (warehouse.getIsDeleted()) {
            throw new RuntimeException("L'entrepôt a été supprimé");
        }
        assertBelongsToCompany(warehouse, companyId);
        
        return mapToDTO(warehouse);
    }
    
    /**
     * Met à jour un entrepôt
     */
    @Transactional
    public WarehouseDTO updateWarehouse(Long companyId, Long id, UpdateWarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entrepôt non trouvé avec l'ID: " + id));
        
        if (warehouse.getIsDeleted()) {
            throw new RuntimeException("L'entrepôt a été supprimé");
        }
        assertBelongsToCompany(warehouse, companyId);
        if (request.getName() != null) {
            warehouse.setName(request.getName());
        }
        if (request.getRegion() != null) {
            warehouse.setRegion(request.getRegion());
        }
        if (request.getDescription() != null) {
            warehouse.setDescription(request.getDescription());
        }
        if (request.getStatusCode() != null) {
            String code = request.getStatusCode().toUpperCase();
            String normalizedCode = "Actif";
            if ("INACTIF".equals(code)) normalizedCode = "Inactif";
            else if ("MAINTENANCE".equals(code)) normalizedCode = "Maintenance";
            else if ("ACTIF".equals(code)) normalizedCode = "Actif";
            warehouseStatusRepository.findById(normalizedCode).ifPresent(warehouse::setStatus);
        }
        
        return mapToDTO(warehouseRepository.save(warehouse));
    }
    
    /**
     * Supprime logiquement un entrepôt (soft delete)
     */
    @Transactional
    public void deleteWarehouse(Long companyId, Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Entrepôt non trouvé avec l'ID: " + id));
        
        if (warehouse.getIsDeleted()) {
            throw new RuntimeException("L'entrepôt a déjà été supprimé");
        }
        assertBelongsToCompany(warehouse, companyId);
        
        warehouse.setIsDeleted(true);
        warehouseRepository.save(warehouse);
    }

    private void assertBelongsToCompany(Warehouse warehouse, Long companyId) {
        if (warehouse.getCompany() == null || !companyId.equals(warehouse.getCompany().getId())) {
            throw new ForbiddenAccessException("Accès non autorisé à cet entrepôt");
        }
    }
    
    /**
     * Mappe un Warehouse vers un WarehouseDTO
     */
    private WarehouseDTO mapToDTO(Warehouse warehouse) {
        return WarehouseDTO.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .region(warehouse.getRegion())
                .description(warehouse.getDescription())
                .statusCode(warehouse.getStatus() != null ? warehouse.getStatus().getCode() : null)
                .statusLabel(warehouse.getStatus() != null ? warehouse.getStatus().getLabel() : null)
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .build();
    }
    
}
