package com.stocksaas.service;

import com.stocksaas.exception.ForbiddenAccessException;
import com.stocksaas.dto.CreateProductRequest;
import com.stocksaas.dto.PageResponse;
import com.stocksaas.dto.ProductDTO;
import com.stocksaas.dto.UpdateProductRequest;
import com.stocksaas.model.*;
import com.stocksaas.repository.ProductCategoryRepository;
import com.stocksaas.repository.ProductRepository;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.WarehouseRepository;
import com.stocksaas.repository.ProductStatusRepository;
import com.stocksaas.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocksaas.util.ProductReferenceUtil;

import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service pour la gestion des produits
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductStatusRepository productStatusRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final StockLevelRepository stockLevelRepository;
    
    /**
     * Crée un nouveau produit
     */
    @Transactional
    public ProductDTO createProduct(Long companyId, CreateProductRequest request) {
        // Vérifier que l'entreprise existe
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée avec l'ID: " + companyId));
        
        if (company.getIsDeleted()) {
            throw new RuntimeException("L'entreprise a été supprimée");
        }

        // Générer automatiquement un SKU unique à partir du nom du produit
        String generatedSku = generateSku(companyId, request.getName());
        
        // Créer le produit
        Product product = new Product();
        product.setCompany(company);
        product.setName(request.getName());
        product.setSku(generatedSku);
        if (request.getCategoryCode() != null && !request.getCategoryCode().isBlank()) {
            productCategoryRepository.findById(request.getCategoryCode())
                    .ifPresent(product::setProductCategory);
        }
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO);
        product.setPurchasePrice(request.getPurchasePrice() != null ? request.getPurchasePrice() : BigDecimal.ZERO);
        
        // Statut initial : Rupture (sera mis à "En stock" si un entrepôt est choisi avec quantité > 0)
        ProductStatus status = productStatusRepository.findById("Rupture")
                .orElseGet(() -> {
                    ProductStatus newStatus = new ProductStatus();
                    newStatus.setCode("Rupture");
                    newStatus.setLabel("Rupture de stock");
                    newStatus.setIsActive(true);
                    return productStatusRepository.save(newStatus);
                });
        product.setStatus(status);
        
        // Sauvegarder le produit
        Product savedProduct = productRepository.save(product);
        
        // Générer la référence explicite : REF_YYYYMMDD_SKU_ID
        String reference = ProductReferenceUtil.buildReference(
                LocalDate.now(), generatedSku, savedProduct.getId());
        savedProduct.setReference(reference);
        savedProduct = productRepository.save(savedProduct);
        
        // Créer un StockLevel dans l'entrepôt par défaut de l'entreprise (si existant)
        BigDecimal initialQty = request.getQuantity() != null && request.getQuantity().compareTo(BigDecimal.ZERO) >= 0
                ? request.getQuantity() : BigDecimal.ZERO;
        // Entrepôt par défaut : créé à l'inscription (nom \"DEFAULT-ENTREPOT\") ou premier entrepôt actif
        List<Warehouse> companyWarehouses = warehouseRepository.findByCompanyIdAndNotDeleted(companyId);
        if (!companyWarehouses.isEmpty()) {
            Warehouse warehouse = resolveTargetWarehouse(companyId, request.getWarehouseId(), companyWarehouses);
            BigDecimal minThreshold = request.getMinThreshold() != null && request.getMinThreshold().compareTo(BigDecimal.ZERO) >= 0
                    ? request.getMinThreshold() : BigDecimal.ZERO;
            StockLevel stockLevel = new StockLevel();
            stockLevel.setProduct(savedProduct);
            stockLevel.setWarehouse(warehouse);
            stockLevel.setQuantity(initialQty);
            stockLevel.setMinThreshold(minThreshold);
            stockLevelRepository.save(stockLevel);
            // Statut "En stock" si quantité > 0
            if (initialQty.compareTo(BigDecimal.ZERO) > 0) {
                ProductStatus enStock = productStatusRepository.findById("En stock")
                        .orElseGet(() -> {
                            ProductStatus s = new ProductStatus();
                            s.setCode("En stock");
                            s.setLabel("En stock");
                            s.setIsActive(true);
                            return productStatusRepository.save(s);
                        });
                savedProduct.setStatus(enStock);
                productRepository.save(savedProduct);
            }
        }

        productRepository.clearUpdatedAt(savedProduct.getId());
        savedProduct = productRepository.findByIdWithStockLevels(savedProduct.getId())
                .orElse(savedProduct);
        savedProduct.setUpdatedAt(null);
        
        return mapToDTO(savedProduct);
    }

    /**
     * Génère un SKU unique pour une entreprise à partir du nom du produit.
     * Exemple : "Téléphone Samsung Galaxy" -> "TELEPHONE-SAMSUNG-GALAXY", avec suffixe numérique si nécessaire.
     */
    private String generateSku(Long companyId, String name) {
        String base;
        if (name == null || name.isBlank()) {
            base = "PROD";
        } else {
            String cleaned = name.toUpperCase().replaceAll("[^A-Z0-9]", " ");
            cleaned = cleaned.trim().replaceAll("\\s+", "-");
            if (cleaned.isBlank()) {
                cleaned = "PROD";
            }
            if (cleaned.length() > 20) {
                cleaned = cleaned.substring(0, 20);
            }
            base = cleaned;
        }

        String candidate = base;
        int suffix = 1;
        while (productRepository.findByCompanyIdAndSku(companyId, candidate).isPresent()) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }
    
    /**
     * Récupère tous les produits d'une entreprise (sans filtre)
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> getAllProductsByCompany(Long companyId) {
        List<Product> products = productRepository.findByCompanyIdAndIsDeletedFalse(companyId);
        return products.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupère les produits d'une entreprise avec filtres optionnels et pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductDTO> getProductsPaged(Long companyId, int page, int size,
            String name, String reference, String sku, String categoryCode,
            LocalDate dateFrom, LocalDate dateTo) {
        Specification<Product> spec = buildProductSpecification(companyId, name, reference, sku, categoryCode, dateFrom, dateTo);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Product> productPage = productRepository.findAll(spec, pageable);
        return PageResponse.<ProductDTO>builder()
                .content(productPage.getContent().stream().map(this::mapToDTO).collect(Collectors.toList()))
                .page(productPage.getNumber())
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .first(productPage.isFirst())
                .last(productPage.isLast())
                .build();
    }

    /**
     * Récupère les produits d'une entreprise avec filtres optionnels.
     * Les critères vides ou null sont ignorés.
     */
    @Transactional(readOnly = true)
    public List<ProductDTO> getAllProductsByCompanyWithFilters(Long companyId,
            String name, String reference, String sku, String categoryCode,
            LocalDate dateFrom, LocalDate dateTo) {
        Specification<Product> spec = buildProductSpecification(companyId, name, reference, sku, categoryCode, dateFrom, dateTo);
        List<Product> products = productRepository.findAll(spec);
        log.debug("Produits trouvés après filtrage: {}", products.size());
        return products.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private Specification<Product> buildProductSpecification(Long companyId,
            String name, String reference, String sku, String categoryCode,
            LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Filtre obligatoire : companyId et isDeleted = false
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            
            // Filtre par nom (LIKE insensible à la casse)
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
            }
            
            // Filtre par référence (LIKE insensible à la casse, seulement si reference n'est pas null)
            if (reference != null && !reference.isBlank()) {
                Predicate refNotNull = cb.isNotNull(root.get("reference"));
                Predicate refLike = cb.like(cb.lower(root.get("reference")), "%" + reference.trim().toLowerCase() + "%");
                predicates.add(cb.and(refNotNull, refLike));
            }
            
            // Filtre par SKU (LIKE insensible à la casse)
            if (sku != null && !sku.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sku")), "%" + sku.trim().toLowerCase() + "%"));
            }
            
            // Filtre par catégorie
            if (categoryCode != null && !categoryCode.isBlank()) {
                Predicate catNotNull = cb.isNotNull(root.get("productCategory"));
                Predicate catEquals = cb.equal(root.get("productCategory").get("code"), categoryCode.trim());
                predicates.add(cb.and(catNotNull, catEquals));
            }
            
            // Filtre par date de création (du)
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
            }
            
            // Filtre par date de création (au)
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo.atTime(LocalTime.MAX)));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Récupère un produit par son ID
     */
    @Transactional(readOnly = true)
    public ProductDTO getProductById(Long companyId, Long id) {
        Product product = productRepository.findByIdWithStockLevels(id)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé avec l'ID: " + id));
        
        if (product.getIsDeleted()) {
            throw new RuntimeException("Le produit a été supprimé");
        }
        assertBelongsToCompany(product, companyId);
        
        return mapToDTO(product);
    }
    
    /**
     * Met à jour un produit
     */
    @Transactional
    public ProductDTO updateProduct(Long companyId, Long id, UpdateProductRequest request) {
        Product product = productRepository.findByIdWithStockLevels(id)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé avec l'ID: " + id));
        
        if (product.getIsDeleted()) {
            throw new RuntimeException("Le produit a été supprimé");
        }
        assertBelongsToCompany(product, companyId);
        // Mettre à jour les champs
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getSku() != null && !request.getSku().equals(product.getSku())) {
            // Vérifier l'unicité du nouveau SKU
            productRepository.findByCompanyIdAndSku(product.getCompany().getId(), request.getSku())
                    .ifPresent(p -> {
                        if (!p.getId().equals(id)) {
                            throw new RuntimeException("Un produit avec le SKU '" + request.getSku() + "' existe déjà");
                        }
                    });
            product.setSku(request.getSku());
        }
        if (request.getCategoryCode() != null) {
            if (request.getCategoryCode().isBlank()) {
                product.setProductCategory(null);
            } else {
                productCategoryRepository.findById(request.getCategoryCode())
                        .ifPresent(product::setProductCategory);
            }
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getPurchasePrice() != null) {
            product.setPurchasePrice(request.getPurchasePrice());
        }
        
        // Mettre à jour le statut et le stock si la quantité change
        // Note: Le stock est géré par entrepôt via StockLevel, pas directement sur le produit
        BigDecimal quantityToUpdate = request.getQuantity() != null ? request.getQuantity() : request.getStock();
        
        if (quantityToUpdate != null) {
            Warehouse warehouse = resolveTargetWarehouse(
                    product.getCompany().getId(),
                    request.getWarehouseId(),
                    warehouseRepository.findByCompanyIdAndNotDeleted(product.getCompany().getId()));

            String statusCode = quantityToUpdate.compareTo(BigDecimal.ZERO) > 0 ? "En stock" : "Rupture";
            ProductStatus status = productStatusRepository.findById(statusCode)
                    .orElseGet(() -> {
                        ProductStatus newStatus = new ProductStatus();
                        newStatus.setCode(statusCode);
                        newStatus.setLabel(statusCode.equals("En stock") ? "En stock" : "Rupture de stock");
                        newStatus.setIsActive(true);
                        return productStatusRepository.save(newStatus);
                    });
            product.setStatus(status);

            final Warehouse targetWarehouse = warehouse;
            StockLevel stockLevel = stockLevelRepository.findByProductIdAndWarehouseId(product.getId(), targetWarehouse.getId())
                    .orElseGet(() -> {
                        StockLevel newStockLevel = new StockLevel();
                        newStockLevel.setProduct(product);
                        newStockLevel.setWarehouse(targetWarehouse);
                        newStockLevel.setMinThreshold(BigDecimal.ZERO);
                        return newStockLevel;
                    });
            stockLevel.setQuantity(quantityToUpdate);
            if (request.getMinThreshold() != null) {
                stockLevel.setMinThreshold(request.getMinThreshold());
            }
            stockLevelRepository.save(stockLevel);
        } else if (request.getMinThreshold() != null) {
            List<Warehouse> companyWarehouses = warehouseRepository.findByCompanyIdAndNotDeleted(product.getCompany().getId());
            if (companyWarehouses.isEmpty()) {
                throw new RuntimeException("Aucun entrepôt trouvé pour cette entreprise");
            }
            Warehouse warehouse = resolveTargetWarehouse(
                    product.getCompany().getId(),
                    request.getWarehouseId(),
                    companyWarehouses);
            StockLevel stockLevel = stockLevelRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                    .orElseGet(() -> {
                        StockLevel newStockLevel = new StockLevel();
                        newStockLevel.setProduct(product);
                        newStockLevel.setWarehouse(warehouse);
                        newStockLevel.setQuantity(BigDecimal.ZERO);
                        return newStockLevel;
                    });
            stockLevel.setMinThreshold(request.getMinThreshold());
            stockLevelRepository.save(stockLevel);
        }

        return mapToDTO(productRepository.findByIdWithStockLevels(id).orElse(product));
    }
    
    /**
     * Supprime logiquement un produit (soft delete)
     */
    @Transactional
    public void deleteProduct(Long companyId, Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produit non trouvé avec l'ID: " + id));
        
        if (product.getIsDeleted()) {
            throw new RuntimeException("Le produit a déjà été supprimé");
        }
        assertBelongsToCompany(product, companyId);
        
        product.setIsDeleted(true);
        productRepository.save(product);
    }

    private void assertBelongsToCompany(Product product, Long companyId) {
        if (product.getCompany() == null || !companyId.equals(product.getCompany().getId())) {
            throw new ForbiddenAccessException("Accès non autorisé à ce produit");
        }
    }
    
    /**
     * Mappe un Product vers un ProductDTO
     */
    private Warehouse resolveTargetWarehouse(Long companyId, Long warehouseId, List<Warehouse> companyWarehouses) {
        if (companyWarehouses == null || companyWarehouses.isEmpty()) {
            throw new RuntimeException("Aucun entrepôt trouvé pour cette entreprise");
        }
        if (warehouseId != null) {
            Warehouse warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new RuntimeException("Entrepôt non trouvé avec l'ID: " + warehouseId));
            if (warehouse.getCompany() == null || !companyId.equals(warehouse.getCompany().getId())) {
                throw new RuntimeException("L'entrepôt n'appartient pas à la même entreprise");
            }
            return warehouse;
        }
        return companyWarehouses.stream()
                .filter(w -> "DEFAULT-ENTREPOT".equalsIgnoreCase(w.getName()))
                .findFirst()
                .orElse(companyWarehouses.get(0));
    }

    static boolean isLowStock(StockLevel stockLevel) {
        if (stockLevel == null) {
            return false;
        }
        BigDecimal min = stockLevel.getMinThreshold() != null ? stockLevel.getMinThreshold() : BigDecimal.ZERO;
        BigDecimal qty = stockLevel.getQuantity() != null ? stockLevel.getQuantity() : BigDecimal.ZERO;
        return min.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(min) <= 0;
    }

    private ProductDTO mapToDTO(Product product) {
        BigDecimal totalStock = BigDecimal.ZERO;
        Long warehouseId = null;
        String warehouseName = null;
        BigDecimal minThreshold = BigDecimal.ZERO;
        boolean lowStock = false;
        if (product.getStockLevels() != null && !product.getStockLevels().isEmpty()) {
            List<StockLevel> activeLevels = product.getStockLevels().stream()
                    .filter(level -> !Boolean.TRUE.equals(level.getIsDeleted()))
                    .filter(level -> level.getWarehouse() != null && !Boolean.TRUE.equals(level.getWarehouse().getIsDeleted()))
                    .toList();
            totalStock = activeLevels.stream()
                    .map(level -> level.getQuantity() != null ? level.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            for (StockLevel level : activeLevels) {
                if (isLowStock(level)) {
                    lowStock = true;
                }
            }
            StockLevel primaryLevel = activeLevels.stream()
                    .filter(level -> level.getQuantity() != null && level.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                    .max((a, b) -> a.getQuantity().compareTo(b.getQuantity()))
                    .orElse(activeLevels.isEmpty() ? null : activeLevels.get(0));
            if (primaryLevel != null && primaryLevel.getWarehouse() != null) {
                warehouseId = primaryLevel.getWarehouse().getId();
                warehouseName = primaryLevel.getWarehouse().getName();
            }
            if (primaryLevel != null && primaryLevel.getMinThreshold() != null) {
                minThreshold = primaryLevel.getMinThreshold();
            }
        }

        String statusCode = totalStock.compareTo(BigDecimal.ZERO) > 0 ? "En stock" : "Rupture";
        String statusLabel = totalStock.compareTo(BigDecimal.ZERO) > 0 ? "En stock" : "Rupture de stock";
        
        String ref = product.getReference();
        if (ref == null && product.getId() != null && product.getCreatedAt() != null) {
            ref = product.getSku() != null
                    ? ProductReferenceUtil.buildReference(
                            product.getCreatedAt().toLocalDate(), product.getSku(), product.getId())
                    : ProductReferenceUtil.buildFallbackReference(
                            product.getCreatedAt().toLocalDate(), product.getId());
        }
        String categoryLabel = product.getProductCategory() != null ? product.getProductCategory().getLabel() : null;
        String categoryCode = product.getProductCategory() != null ? product.getProductCategory().getCode() : null;
        return ProductDTO.builder()
                .id(product.getId())
                .reference(ref)
                .name(product.getName())
                .sku(product.getSku())
                .category(categoryLabel)
                .categoryCode(categoryCode)
                .description(product.getDescription())
                .price(product.getPrice())
                .purchasePrice(product.getPurchasePrice())
                .statusCode(statusCode)
                .statusLabel(statusLabel)
                .warehouseId(warehouseId)
                .warehouseName(warehouseName)
                .stock(totalStock)
                .minThreshold(minThreshold)
                .lowStock(lowStock)
                .createdAt(product.getCreatedAt())
                .updatedAt(resolveUpdatedAt(product.getCreatedAt(), product.getUpdatedAt()))
                .build();
    }

    private static LocalDateTime resolveUpdatedAt(LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (updatedAt == null || createdAt == null) {
            return null;
        }
        return updatedAt.isAfter(createdAt) ? updatedAt : null;
    }
}
