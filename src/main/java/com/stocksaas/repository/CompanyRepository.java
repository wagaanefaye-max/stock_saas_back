package com.stocksaas.repository;

import com.stocksaas.dto.CompanyDTO;
import com.stocksaas.model.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("SELECT COUNT(c) FROM Company c WHERE c.isDeleted = false "
            + "AND c.status IS NOT NULL AND LOWER(c.status.code) = 'actif'")
    long countActiveCompanies();

    @Query("SELECT COUNT(c) FROM Company c WHERE c.isDeleted = false "
            + "AND c.createdAt >= :start AND c.createdAt < :end")
    long countCreatedBetween(@Param("start") java.time.LocalDateTime start,
                             @Param("end") java.time.LocalDateTime end);
    
    /**
     * Compte les entreprises créées par année/mois (agrégation SQL, sans dépendre de la locale PostgreSQL).
     */
    @Query(value = "SELECT EXTRACT(YEAR FROM c.created_at)::int, EXTRACT(MONTH FROM c.created_at)::int, COUNT(c.id) "
            + "FROM td_companies c "
            + "WHERE c.created_at >= CAST(:startDate AS timestamp) AND c.is_deleted = false "
            + "GROUP BY EXTRACT(YEAR FROM c.created_at), EXTRACT(MONTH FROM c.created_at)", nativeQuery = true)
    List<Object[]> countCompaniesByYearMonth(@Param("startDate") java.time.LocalDateTime startDate);

    @Query(value = "SELECT "
            + "COALESCE(SUM(CASE WHEN c.created_at >= :currentStart AND c.created_at < :currentEnd THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN c.created_at >= :prevStart AND c.created_at < :prevEnd THEN 1 ELSE 0 END), 0) "
            + "FROM td_companies c WHERE c.is_deleted = false", nativeQuery = true)
    Object[] countCompaniesCreatedCurrentAndPrevious(@Param("currentStart") java.time.LocalDateTime currentStart,
                                                     @Param("currentEnd") java.time.LocalDateTime currentEnd,
                                                     @Param("prevStart") java.time.LocalDateTime prevStart,
                                                     @Param("prevEnd") java.time.LocalDateTime prevEnd);

    @Query("SELECT DISTINCT c FROM Company c LEFT JOIN FETCH c.plan LEFT JOIN FETCH c.status "
            + "WHERE c.isDeleted = false ORDER BY c.createdAt DESC")
    List<Company> findRecentWithPlanAndStatus(Pageable pageable);
    
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

}
