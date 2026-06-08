package com.stocksaas.repository;

import com.stocksaas.model.WarehouseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour l'entité WarehouseStatus (table de référence)
 */
@Repository
public interface WarehouseStatusRepository extends JpaRepository<WarehouseStatus, String> {
}
