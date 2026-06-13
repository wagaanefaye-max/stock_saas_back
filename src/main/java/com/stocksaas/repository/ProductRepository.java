package com.stocksaas.repository;

import com.stocksaas.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité Product
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    
    /**
     * Compte les produits non supprimés d'une entreprise
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.company.id = :companyId AND p.isDeleted = false")
    Long countByCompanyIdAndNotDeleted(@Param("companyId") Long companyId);

    /**
     * Liste des produits d'une entreprise non supprimés
     */
    @Query("SELECT p FROM Product p WHERE p.company.id = :companyId AND p.isDeleted = false ORDER BY p.id DESC")
    List<Product> findByCompanyIdAndIsDeletedFalse(@Param("companyId") Long companyId);

    /**
     * Liste des produits d'une entreprise avec filtres optionnels.
     * Utilise une requête native SQL pour éviter les problèmes de type bytea avec PostgreSQL.
     * Les patterns de recherche (namePattern, referencePattern, skuPattern) doivent déjà contenir les '%' autour.
     */
    @Query(value = "SELECT p.* FROM td_products p " +
           "WHERE p.company_id = :companyId AND p.is_deleted = false " +
           "AND (:namePattern IS NULL OR LOWER(p.name) LIKE LOWER(CAST(:namePattern AS TEXT))) " +
           "AND (:referencePattern IS NULL OR (p.reference IS NOT NULL AND LOWER(p.reference) LIKE LOWER(CAST(:referencePattern AS TEXT)))) " +
           "AND (:skuPattern IS NULL OR LOWER(p.sku) LIKE LOWER(CAST(:skuPattern AS TEXT))) " +
           "AND (:categoryCode IS NULL OR (p.category_code IS NOT NULL AND p.category_code = CAST(:categoryCode AS TEXT))) " +
           "AND (:dateFrom IS NULL OR p.created_at >= :dateFrom) " +
           "AND (:dateTo IS NULL OR p.created_at <= :dateTo) " +
           "ORDER BY p.id DESC",
           nativeQuery = true)
    List<Product> findByCompanyIdAndFilters(
            @Param("companyId") Long companyId,
            @Param("namePattern") String namePattern,
            @Param("referencePattern") String referencePattern,
            @Param("skuPattern") String skuPattern,
            @Param("categoryCode") String categoryCode,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo);
    
    /**
     * Compte les produits par catégorie pour une entreprise
     * Note: Les produits appartiennent à l'entreprise, pas à un entrepôt spécifique
     */
    @Query("SELECT p.productCategory, COUNT(p) FROM Product p " +
           "WHERE p.company.id = :companyId AND p.isDeleted = false AND p.productCategory IS NOT NULL " +
           "GROUP BY p.productCategory")
    List<Object[]> countProductsByCategory(@Param("companyId") Long companyId);
    
    /**
     * Trouve un produit par entreprise et SKU
     */
    @Query("SELECT p FROM Product p WHERE p.company.id = :companyId AND p.sku = :sku AND p.isDeleted = false")
    Optional<Product> findByCompanyIdAndSku(@Param("companyId") Long companyId, @Param("sku") String sku);
    
    /**
     * Trouve un produit par ID avec ses StockLevels et company (évite N+1 sur getCompany().getId()).
     */
    @EntityGraph(attributePaths = {"stockLevels", "stockLevels.warehouse", "company"})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithStockLevels(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.updatedAt = null WHERE p.id = :id")
    void clearUpdatedAt(@Param("id") Long id);
}
