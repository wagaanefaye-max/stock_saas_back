package com.stocksaas.repository;

import com.stocksaas.dto.CompanyDTO;
import com.stocksaas.model.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité Company
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    
    /**
     * Trouve une entreprise par son nom
     */
    Optional<Company> findByName(String name);
    
    /**
     * Trouve une entreprise par son email
     */
    Optional<Company> findByEmail(String email);
    
    /**
     * Récupère toutes les entreprises avec pagination
     */
    @Query("SELECT c FROM Company c WHERE c.isDeleted = false")
    Page<Company> findAllNotDeleted(Pageable pageable);
    
    /**
     * Récupère les entreprises avec plan et statut chargés (évite N+1).
     */
    @Query(value = "SELECT DISTINCT c FROM Company c LEFT JOIN FETCH c.plan LEFT JOIN FETCH c.status WHERE c.isDeleted = false",
           countQuery = "SELECT COUNT(c) FROM Company c WHERE c.isDeleted = false")
    Page<Company> findAllNotDeletedWithPlanAndStatus(Pageable pageable);
    
    /**
     * Récupère les entreprises avec recherche, plan et statut chargés (évite N+1).
     */
    @Query(value = "SELECT DISTINCT c FROM Company c LEFT JOIN FETCH c.plan LEFT JOIN FETCH c.status WHERE c.isDeleted = false " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(c) FROM Company c WHERE c.isDeleted = false " +
                   "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Company> findAllNotDeletedWithSearchAndPlanAndStatus(@Param("search") String search, Pageable pageable);
    
    /**
     * Récupère une entreprise par ID avec plan, statut, utilisateurs et rôles (une seule requête pour le détail).
     */
    @Query("SELECT DISTINCT c FROM Company c LEFT JOIN FETCH c.plan LEFT JOIN FETCH c.status " +
           "LEFT JOIN FETCH c.users u LEFT JOIN FETCH u.role WHERE c.id = :id AND c.isDeleted = false")
    Optional<Company> findByIdWithPlanStatusAndUsers(@Param("id") Long id);
    
    // --- Méthodes retournant directement des DTO (une seule requête par opération) ---
    
    /**
     * Liste paginée des entreprises en DTO (projection JPQL, une requête).
     */
    @Query(value = "SELECT new com.stocksaas.dto.CompanyDTO(" +
           "c.id, c.name, c.email, c.phone, c.address, c.region, c.country, " +
           "c.plan.code, c.plan.label, c.status.code, c.status.label, c.logoUrl, " +
           "(SELECT COUNT(u) FROM User u WHERE u.company = c AND u.isDeleted = false), " +
           "c.createdAt, " +
           "(SELECT MIN(u.name) FROM User u WHERE u.company = c AND u.isDeleted = false AND u.role.code = 'ADMIN_ENTREPRISE'), " +
           "(SELECT MIN(u.email) FROM User u WHERE u.company = c AND u.isDeleted = false AND u.role.code = 'ADMIN_ENTREPRISE'), " +
           "c.notifLowStock, c.notifMovements, c.notifReports, " +
           "c.subscriptionStatus, c.trialEndsAt, c.subscriptionEndsAt, c.durationCode) " +
           "FROM Company c LEFT JOIN c.plan LEFT JOIN c.status WHERE c.isDeleted = false",
           countQuery = "SELECT COUNT(c) FROM Company c WHERE c.isDeleted = false")
    Page<CompanyDTO> findAllNotDeletedAsDTO(Pageable pageable);
    
    /**
     * Liste paginée des entreprises avec recherche, en DTO (projection JPQL, une requête).
     */
    @Query(value = "SELECT new com.stocksaas.dto.CompanyDTO(" +
           "c.id, c.name, c.email, c.phone, c.address, c.region, c.country, " +
           "c.plan.code, c.plan.label, c.status.code, c.status.label, c.logoUrl, " +
           "(SELECT COUNT(u) FROM User u WHERE u.company = c AND u.isDeleted = false), " +
           "c.createdAt, " +
           "(SELECT MIN(u.name) FROM User u WHERE u.company = c AND u.isDeleted = false AND u.role.code = 'ADMIN_ENTREPRISE'), " +
           "(SELECT MIN(u.email) FROM User u WHERE u.company = c AND u.isDeleted = false AND u.role.code = 'ADMIN_ENTREPRISE'), " +
           "c.notifLowStock, c.notifMovements, c.notifReports, " +
           "c.subscriptionStatus, c.trialEndsAt, c.subscriptionEndsAt, c.durationCode) " +
           "FROM Company c LEFT JOIN c.plan LEFT JOIN c.status WHERE c.isDeleted = false " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(c) FROM Company c WHERE c.isDeleted = false " +
                   "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CompanyDTO> findAllNotDeletedWithSearchAsDTO(@Param("search") String search, Pageable pageable);
    
    /**
     * Détail d'une entreprise par ID en DTO (une requête).
     */
    @Query("SELECT new com.stocksaas.dto.CompanyDTO(" +
           "c.id, c.name, c.email, c.phone, c.address, c.region, c.country, " +
           "c.plan.code, c.plan.label, c.status.code, c.status.label, c.logoUrl, " +
           "(SELECT COUNT(u) FROM User u WHERE u.company = c AND u.isDeleted = false), " +
           "c.createdAt, " +
           "(SELECT MIN(u.name) FROM User u WHERE u.company = c AND u.isDeleted = false AND u.role.code = 'ADMIN_ENTREPRISE'), " +
           "(SELECT MIN(u.email) FROM User u WHERE u.company = c AND u.isDeleted = false AND u.role.code = 'ADMIN_ENTREPRISE'), " +
           "c.notifLowStock, c.notifMovements, c.notifReports, " +
           "c.subscriptionStatus, c.trialEndsAt, c.subscriptionEndsAt, c.durationCode) " +
           "FROM Company c LEFT JOIN c.plan LEFT JOIN c.status WHERE c.id = :id AND c.isDeleted = false")
    Optional<CompanyDTO> findDTOById(@Param("id") Long id);
    
    /**
     * Récupère les entreprises avec recherche par nom ou email
     */
    @Query("SELECT c FROM Company c WHERE c.isDeleted = false " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Company> findAllNotDeletedWithSearch(@Param("search") String search, Pageable pageable);
    
    /**
     * Compte les entreprises créées par mois (6 derniers mois)
     */
    @Query(value = "SELECT TO_CHAR(c.created_at, 'Mon') as month, COUNT(c.id) as count " +
           "FROM td_companies c " +
           "WHERE c.created_at >= CAST(:startDate AS timestamp) AND c.is_deleted = false " +
           "GROUP BY TO_CHAR(c.created_at, 'Mon'), EXTRACT(YEAR FROM c.created_at), EXTRACT(MONTH FROM c.created_at) " +
           "ORDER BY EXTRACT(YEAR FROM c.created_at) DESC, EXTRACT(MONTH FROM c.created_at) DESC", nativeQuery = true)
    List<Object[]> countCompaniesByMonth(@Param("startDate") java.time.LocalDateTime startDate);
    
    /**
     * Compte les entreprises par plan d'abonnement
     * Utilise COALESCE pour gérer les cas où le plan est null ou la table n'existe pas
     */
    @Query(value = "SELECT COALESCE(c.plan_code, 'N/A') as plan, COUNT(c.id) as count " +
           "FROM td_companies c " +
           "WHERE c.is_deleted = false " +
           "GROUP BY c.plan_code", nativeQuery = true)
    List<Object[]> countCompaniesByPlan();

    /**
     * Entreprises en essai ou abonnement actif (candidates aux rappels avant expiration).
     */
    @Query("SELECT c FROM Company c WHERE c.isDeleted = false " +
           "AND c.subscriptionStatus IN ('TRIAL', 'ACTIVE')")
    List<Company> findCompaniesWithTrialOrActiveSubscription();

    @Query("SELECT c FROM Company c WHERE c.isDeleted = false " +
           "AND (c.notifLowStock = true OR c.notifLowStock IS NULL)")
    List<Company> findActiveWithLowStockNotificationsEnabled();

    @Query("SELECT c FROM Company c WHERE c.isDeleted = false AND c.notifReports = true")
    List<Company> findActiveWithWeeklyReportEnabled();

    @Query("""
            SELECT COUNT(c) FROM Company c
            WHERE c.isDeleted = false
            AND c.status IS NOT NULL
            AND LOWER(c.status.code) IN ('actif')
            """)
    long countActiveNotDeleted();

    @Query("""
            SELECT COUNT(c) FROM Company c
            WHERE c.isDeleted = false
            AND c.createdAt >= :start
            AND c.createdAt < :end
            """)
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT DISTINCT c FROM Company c
            LEFT JOIN FETCH c.plan
            LEFT JOIN FETCH c.status
            WHERE (c.isDeleted = false OR c.isDeleted IS NULL)
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<Company> findRecentNotDeletedWithPlanAndStatus(Pageable pageable);

    @Query(value = """
            SELECT c.id,
                   c.name,
                   COALESCE(r.out_of_stock_count, 0) AS out_of_stock_count,
                   COALESCE(l.low_stock_count, 0) AS low_stock_count,
                   (COALESCE(r.out_of_stock_count, 0) * 3 + COALESCE(l.low_stock_count, 0) * 2) AS risk_score
            FROM td_companies c
            LEFT JOIN (
                SELECT p.company_id AS company_id, COUNT(DISTINCT p.id) AS out_of_stock_count
                FROM td_products p
                LEFT JOIN (
                    SELECT sl.product_id, COALESCE(SUM(sl.quantity), 0) AS total_qty
                    FROM td_stock_levels sl
                    INNER JOIN td_warehouses w ON w.id = sl.warehouse_id
                    WHERE w.is_deleted IS NOT TRUE
                      AND sl.is_deleted IS NOT TRUE
                    GROUP BY sl.product_id
                ) totals ON totals.product_id = p.id
                WHERE p.is_deleted IS NOT TRUE
                  AND (totals.total_qty IS NULL OR totals.total_qty <= 0)
                GROUP BY p.company_id
            ) r ON r.company_id = c.id
            LEFT JOIN (
                SELECT p.company_id AS company_id, COUNT(DISTINCT p.id) AS low_stock_count
                FROM td_stock_levels sl
                INNER JOIN td_products p ON p.id = sl.product_id
                INNER JOIN td_warehouses w ON w.id = sl.warehouse_id
                WHERE p.is_deleted IS NOT TRUE
                  AND w.is_deleted IS NOT TRUE
                  AND sl.is_deleted IS NOT TRUE
                  AND sl.min_threshold > 0
                  AND sl.quantity <= sl.min_threshold
                GROUP BY p.company_id
            ) l ON l.company_id = c.id
            WHERE c.is_deleted IS NOT TRUE
              AND (COALESCE(r.out_of_stock_count, 0) > 0 OR COALESCE(l.low_stock_count, 0) > 0)
            ORDER BY risk_score DESC, out_of_stock_count DESC, low_stock_count DESC, c.name ASC
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> findTopCompaniesByProductRisk();

}
