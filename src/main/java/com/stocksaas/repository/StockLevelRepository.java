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
}
