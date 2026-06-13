package com.stocksaas.repository;

import com.stocksaas.model.UserWarehouse;
import com.stocksaas.model.UserWarehouseId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository pour l'entité UserWarehouse (relation utilisateur-entrepôt)
 */
@Repository
public interface UserWarehouseRepository extends JpaRepository<UserWarehouse, UserWarehouseId> {

    @Query("SELECT uw.warehouse.id FROM UserWarehouse uw WHERE uw.user.id = :userId AND uw.warehouse.id IS NOT NULL")
    List<Long> findWarehouseIdsByUserId(@Param("userId") Long userId);
}
