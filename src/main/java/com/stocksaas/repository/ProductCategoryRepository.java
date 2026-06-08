package com.stocksaas.repository;

import com.stocksaas.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité ProductCategory (table de référence tp_category)
 */
@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, String> {

    List<ProductCategory> findByIsActiveTrueOrderByLabel();

    @Query("SELECT c FROM ProductCategory c WHERE LOWER(c.label) = LOWER(:label)")
    Optional<ProductCategory> findByLabelIgnoreCase(@Param("label") String label);
}
