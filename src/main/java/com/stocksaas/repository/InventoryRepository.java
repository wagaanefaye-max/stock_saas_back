package com.stocksaas.repository;

import com.stocksaas.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query("SELECT i FROM Inventory i JOIN FETCH i.warehouse JOIN FETCH i.company WHERE i.company.id = :companyId AND i.isDeleted = false ORDER BY i.inventoryDate DESC, i.id DESC")
    List<Inventory> findByCompanyIdAndNotDeleted(@Param("companyId") Long companyId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.warehouse JOIN FETCH i.company LEFT JOIN FETCH i.createdBy " +
           "WHERE i.company.id = :companyId AND i.warehouse.id IN :warehouseIds AND i.isDeleted = false ORDER BY i.inventoryDate DESC, i.id DESC")
    List<Inventory> findByCompanyIdAndWarehouseIdsAndNotDeleted(@Param("companyId") Long companyId, @Param("warehouseIds") List<Long> warehouseIds);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.company JOIN FETCH i.warehouse LEFT JOIN FETCH i.createdBy WHERE i.id = :id AND i.isDeleted = false")
    Optional<Inventory> findByIdWithWarehouseAndCompany(@Param("id") Long id);

    @Query("SELECT i FROM Inventory i WHERE i.company.id = :companyId AND i.warehouse.id = :warehouseId AND i.isDeleted = false AND i.status IN ('DRAFT', 'IN_PROGRESS')")
    List<Inventory> findOpenByCompanyIdAndWarehouseId(@Param("companyId") Long companyId, @Param("warehouseId") Long warehouseId);
}
