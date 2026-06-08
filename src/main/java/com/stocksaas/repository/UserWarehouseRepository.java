package com.stocksaas.repository;

import com.stocksaas.model.UserWarehouse;
import com.stocksaas.model.UserWarehouseId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour l'entité UserWarehouse (relation utilisateur-entrepôt)
 */
@Repository
public interface UserWarehouseRepository extends JpaRepository<UserWarehouse, UserWarehouseId> {
}
