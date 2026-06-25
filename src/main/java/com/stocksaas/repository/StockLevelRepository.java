package com.stocksaas.repository;

import com.stocksaas.model.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité StockLevel
 */
@Repository
public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {
    
    /**
     * Trouve un niveau de stock par produit et entrepôt
     */
    @Query("SELECT sl FROM StockLevel sl WHERE sl.product.id = :productId AND sl.warehouse.id = :warehouseId")
    Optional<StockLevel> findByProductIdAndWarehouseId(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    @Query("""
            SELECT sl FROM StockLevel sl
            WHERE sl.product.id = :productId
            AND sl.warehouse.id = :warehouseId
            AND sl.isDeleted = false
            """)
    Optional<StockLevel> findActiveByProductIdAndWarehouseId(
            @Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId);
    
    /**
     * Trouve tous les niveaux de stock d'un entrepôt (avec produit chargé)
     */
    @Query("SELECT sl FROM StockLevel sl JOIN FETCH sl.product p WHERE sl.warehouse.id = :warehouseId")
    List<StockLevel> findByWarehouseIdWithProduct(@Param("warehouseId") Long warehouseId);

    /**
     * Trouve tous les niveaux de stock d'un produit (avec entrepôt chargé)
     */
    @Query("SELECT sl FROM StockLevel sl JOIN FETCH sl.warehouse w WHERE sl.product.id = :productId")
    List<StockLevel> findByProductIdWithWarehouse(@Param("productId") Long productId);

    @Query("""
            SELECT sl FROM StockLevel sl
            JOIN FETCH sl.warehouse w
            WHERE sl.product.id = :productId
            AND sl.isDeleted = false
            AND w.isDeleted = false
            """)
    List<StockLevel> findActiveByProductIdWithWarehouse(@Param("productId") Long productId);

    /**
     * Nombre d'alertes stock bas : seuil min &gt; 0 et quantité ≤ seuil.
     */
    @Query("""
            SELECT COUNT(sl) FROM StockLevel sl
            JOIN sl.product p
            JOIN sl.warehouse w
            WHERE p.company.id = :companyId
            AND p.isDeleted = false
            AND w.isDeleted = false
            AND sl.minThreshold > 0
            AND sl.quantity <= sl.minThreshold
            AND (:restrictWarehouses = false OR w.id IN :warehouseIds)
            """)
    long countLowStockByCompany(
            @Param("companyId") Long companyId,
            @Param("restrictWarehouses") boolean restrictWarehouses,
            @Param("warehouseIds") List<Long> warehouseIds);

    /**
     * Nombre de produits distincts en stock bas pour une entreprise.
     */
    @Query("""
            SELECT COUNT(DISTINCT p.id) FROM StockLevel sl
            JOIN sl.product p
            JOIN sl.warehouse w
            WHERE p.company.id = :companyId
            AND p.isDeleted = false
            AND w.isDeleted = false
            AND sl.minThreshold > 0
            AND sl.quantity <= sl.minThreshold
            """)
    long countDistinctLowStockProductsByCompany(@Param("companyId") Long companyId);

    @Query(value = """
            SELECT DISTINCT ON (p.id) p.id, p.name, sl.quantity, sl.min_threshold
            FROM td_stock_levels sl
            INNER JOIN td_products p ON p.id = sl.product_id
            INNER JOIN td_warehouses w ON w.id = sl.warehouse_id
            WHERE p.company_id = :companyId
            AND p.is_deleted = false
            AND w.is_deleted = false
            AND sl.min_threshold > 0
            AND sl.quantity <= sl.min_threshold
            ORDER BY p.id, sl.quantity ASC
            LIMIT 8
            """, nativeQuery = true)
    List<Object[]> findLowStockProductsForDashboard(@Param("companyId") Long companyId);

    @Query(value = """
            SELECT p.name, w.name, sl.quantity, sl.min_threshold
            FROM td_stock_levels sl
            INNER JOIN td_products p ON p.id = sl.product_id
            INNER JOIN td_warehouses w ON w.id = sl.warehouse_id
            WHERE p.company_id = :companyId
            AND p.is_deleted = false
            AND w.is_deleted = false
            AND sl.is_deleted = false
            AND sl.min_threshold > 0
            AND sl.quantity <= sl.min_threshold
            ORDER BY sl.quantity ASC, p.name ASC
            LIMIT 50
            """, nativeQuery = true)
    List<Object[]> findLowStockDetailsByCompany(@Param("companyId") Long companyId);

    @Query(value = """
            SELECT COUNT(DISTINCT p.id)
            FROM td_stock_levels sl
            INNER JOIN td_products p ON p.id = sl.product_id
            INNER JOIN td_warehouses w ON w.id = sl.warehouse_id
            WHERE p.is_deleted = false
            AND w.is_deleted = false
            AND sl.is_deleted = false
            AND sl.quantity <= 0
            """, nativeQuery = true)
    long countDistinctOutOfStockProductsPlatform();

    @Query(value = """
            SELECT COUNT(DISTINCT p.id)
            FROM td_stock_levels sl
            INNER JOIN td_products p ON p.id = sl.product_id
            INNER JOIN td_warehouses w ON w.id = sl.warehouse_id
            WHERE p.is_deleted = false
            AND w.is_deleted = false
            AND sl.is_deleted = false
            AND sl.min_threshold > 0
            AND sl.quantity <= sl.min_threshold
            """, nativeQuery = true)
    long countDistinctLowStockProductsPlatform();

    @Query(value = """
            SELECT c.id,
                   c.name,
                   COALESCE(r.out_of_stock_count, 0) AS out_of_stock_count,
                   COALESCE(l.low_stock_count, 0) AS low_stock_count,
                   (COALESCE(r.out_of_stock_count, 0) * 3 + COALESCE(l.low_stock_count, 0) * 2) AS risk_score
            FROM td_companies c
            LEFT JOIN (
                SELECT p.company_id AS company_id, COUNT(DISTINCT p.id) AS out_of_stock_count
                FROM td_stock_levels sl
                INNER JOIN td_products p ON p.id = sl.product_id
                INNER JOIN td_warehouses w ON w.id = sl.warehouse_id
                WHERE p.is_deleted = false
                  AND w.is_deleted = false
                  AND sl.is_deleted = false
                  AND sl.quantity <= 0
                GROUP BY p.company_id
            ) r ON r.company_id = c.id
            LEFT JOIN (
                SELECT p.company_id AS company_id, COUNT(DISTINCT p.id) AS low_stock_count
                FROM td_stock_levels sl
                INNER JOIN td_products p ON p.id = sl.product_id
                INNER JOIN td_warehouses w ON w.id = sl.warehouse_id
                WHERE p.is_deleted = false
                  AND w.is_deleted = false
                  AND sl.is_deleted = false
                  AND sl.min_threshold > 0
                  AND sl.quantity <= sl.min_threshold
                GROUP BY p.company_id
            ) l ON l.company_id = c.id
            WHERE c.is_deleted = false
              AND (COALESCE(r.out_of_stock_count, 0) > 0 OR COALESCE(l.low_stock_count, 0) > 0)
            ORDER BY risk_score DESC, out_of_stock_count DESC, low_stock_count DESC, c.name ASC
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> findTopCompaniesByProductRisk();
}
