package com.stocksaas.repository;

import com.stocksaas.dto.WarehouseDTOForCreatingProduct;
import com.stocksaas.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository pour l'entité Warehouse
 */
@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    
    /**
     * Trouve tous les entrepôts non supprimés d'une entreprise
     */
    @Query("SELECT w FROM Warehouse w JOIN FETCH w.company WHERE w.company.id = :companyId AND w.isDeleted = false")
    List<Warehouse> findByCompanyIdAndNotDeleted(@Param("companyId") Long companyId);
    
    /**
     * Trouve les entrepôts par IDs et entreprise
     */
    @Query("SELECT DISTINCT w FROM Warehouse w JOIN FETCH w.company WHERE w.company.id = :companyId AND w.id IN :warehouseIds AND w.isDeleted = false order by w.createdAt DESC")
    List<Warehouse> findByCompanyIdAndIdsAndNotDeleted(@Param("companyId") Long companyId, @Param("warehouseIds") List<Long> warehouseIds);
}
