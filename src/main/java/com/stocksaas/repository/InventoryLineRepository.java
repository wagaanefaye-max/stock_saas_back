package com.stocksaas.repository;

import com.stocksaas.model.InventoryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryLineRepository extends JpaRepository<InventoryLine, Long> {

    @Query("SELECT il FROM InventoryLine il JOIN FETCH il.product p WHERE il.inventory.id = :inventoryId AND il.isDeleted = false ORDER BY p.name")
    List<InventoryLine> findByInventoryIdWithProduct(@Param("inventoryId") Long inventoryId);

    @Query("SELECT il FROM InventoryLine il JOIN FETCH il.product JOIN FETCH il.inventory WHERE il.inventory.id = :inventoryId AND il.product.id = :productId AND il.isDeleted = false")
    Optional<InventoryLine> findByInventoryIdAndProductId(@Param("inventoryId") Long inventoryId, @Param("productId") Long productId);
}
