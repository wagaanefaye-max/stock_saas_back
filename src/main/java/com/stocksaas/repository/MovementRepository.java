package com.stocksaas.repository;

import com.stocksaas.model.Movement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository pour l'entité Movement
 */
@Repository
public interface MovementRepository extends JpaRepository<Movement, Long>, JpaSpecificationExecutor<Movement> {
    
    /**
     * Compte les mouvements du mois en cours pour une entreprise
     */
    @Query("SELECT COUNT(m) FROM Movement m WHERE m.company.id = :companyId " +
           "AND m.date >= :startDate AND m.date <= :endDate AND m.isDeleted = false")
    Long countByCompanyIdAndDateRange(@Param("companyId") Long companyId,
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);
    
    /**
     * Compte les mouvements du mois en cours pour une entreprise et des entrepôts spécifiques
     */
    @Query("SELECT COUNT(m) FROM Movement m WHERE m.company.id = :companyId " +
           "AND m.warehouse.id IN :warehouseIds " +
           "AND m.date >= :startDate AND m.date <= :endDate AND m.isDeleted = false")
    Long countByCompanyIdAndWarehouseIdsAndDateRange(@Param("companyId") Long companyId,
                                                      @Param("warehouseIds") List<Long> warehouseIds,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);
    
    /**
     * Récupère les mouvements récents
     */
    @Query("SELECT m FROM Movement m WHERE m.company.id = :companyId AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<Movement> findRecentByCompanyId(@Param("companyId") Long companyId, 
                                         org.springframework.data.domain.Pageable pageable);
    
    /**
     * Récupère les mouvements des 6 derniers mois groupés par mois et type
     */
    @Query(value = "SELECT CAST(EXTRACT(YEAR FROM m.date) AS INTEGER) as year, " +
           "CAST(EXTRACT(MONTH FROM m.date) AS INTEGER) as month, " +
           "m.type_code as typeCode, COUNT(m.id) as count " +
           "FROM td_movements m " +
           "WHERE m.company_id = :companyId " +
           "AND m.date >= :startDate " +
           "AND m.is_deleted = false " +
           "GROUP BY EXTRACT(YEAR FROM m.date), EXTRACT(MONTH FROM m.date), m.type_code " +
           "ORDER BY year DESC, month DESC", nativeQuery = true)
    List<Object[]> findMonthlyMovementsByCompany(@Param("companyId") Long companyId, 
                                                  @Param("startDate") LocalDate startDate);
    
    /**
     * Récupère les mouvements des 6 derniers mois groupés par mois et type pour des entrepôts spécifiques
     */
    @Query(value = "SELECT CAST(EXTRACT(YEAR FROM m.date) AS INTEGER) as year, " +
           "CAST(EXTRACT(MONTH FROM m.date) AS INTEGER) as month, " +
           "m.type_code as typeCode, COUNT(m.id) as count " +
           "FROM td_movements m " +
           "WHERE m.company_id = :companyId " +
           "AND m.warehouse_id IN :warehouseIds " +
           "AND m.date >= :startDate " +
           "AND m.is_deleted = false " +
           "GROUP BY EXTRACT(YEAR FROM m.date), EXTRACT(MONTH FROM m.date), m.type_code " +
           "ORDER BY year DESC, month DESC", nativeQuery = true)
    List<Object[]> findMonthlyMovementsByCompanyAndWarehouses(@Param("companyId") Long companyId,
                                                               @Param("warehouseIds") List<Long> warehouseIds,
                                                               @Param("startDate") LocalDate startDate);
    
    /**
     * Récupère tous les mouvements d'une entreprise
     */
    @Query("SELECT m FROM Movement m JOIN FETCH m.product JOIN FETCH m.warehouse JOIN FETCH m.type JOIN FETCH m.user " +
           "LEFT JOIN FETCH m.destinationWarehouse " +
           "WHERE m.company.id = :companyId AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<Movement> findByCompanyId(@Param("companyId") Long companyId);
    
    /**
     * Récupère les mouvements d'une entreprise filtrés par entrepôts
     */
    @Query("SELECT DISTINCT m FROM Movement m JOIN FETCH m.product JOIN FETCH m.warehouse JOIN FETCH m.type JOIN FETCH m.user " +
           "LEFT JOIN FETCH m.destinationWarehouse " +
           "WHERE m.company.id = :companyId AND m.warehouse.id IN :warehouseIds AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<Movement> findByCompanyIdAndWarehouseIds(@Param("companyId") Long companyId,
                                                  @Param("warehouseIds") List<Long> warehouseIds);
}
