package com.stocksaas.repository;

import com.stocksaas.model.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour l'entité ProductStatus (table de référence)
 */
@Repository
public interface ProductStatusRepository extends JpaRepository<ProductStatus, String> {
}
