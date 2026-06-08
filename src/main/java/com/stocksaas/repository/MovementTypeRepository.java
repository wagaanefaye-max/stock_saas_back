package com.stocksaas.repository;

import com.stocksaas.model.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository pour l'entité MovementType (table de référence)
 */
@Repository
public interface MovementTypeRepository extends JpaRepository<MovementType, String> {
    
    /**
     * Récupère tous les types de mouvements actifs
     */
    List<MovementType> findByIsActiveTrue();
}
