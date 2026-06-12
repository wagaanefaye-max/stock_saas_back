package com.stocksaas.repository;

import com.stocksaas.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité User
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Trouve un utilisateur par email
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Trouve un utilisateur par email en chargeant les relations company et role
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.company LEFT JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmailWithCompanyAndRole(@Param("email") String email);
    
    /**
     * Vérifie si un email existe
     */
    boolean existsByEmail(String email);
    
    /**
     * Trouve un utilisateur par email en ignorant le soft delete
     */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.isDeleted = false")
    Optional<User> findByEmailAndNotDeleted(@Param("email") String email);
    
    /**
     * Récupère tous les utilisateurs sauf les SUPER_ADMIN avec pagination
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.role.code != 'SUPER_ADMIN'")
    Page<User> findAllExceptSuperAdmin(Pageable pageable);
    
    /**
     * Récupère les utilisateurs sauf SUPER_ADMIN avec recherche par nom ou email
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.role.code != 'SUPER_ADMIN' " +
           "AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findAllExceptSuperAdminWithSearch(@Param("search") String search, Pageable pageable);

    /**
     * Gestionnaires d'une entreprise (pagination).
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.company.id = :companyId AND u.role.code = 'GESTIONNAIRE'")
    Page<User> findGestionnairesByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    /**
     * Gestionnaires d'une entreprise avec recherche (pagination).
     */
    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND u.company.id = :companyId AND u.role.code = 'GESTIONNAIRE' " +
           "AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findGestionnairesByCompanyIdWithSearch(@Param("companyId") Long companyId,
                                                      @Param("search") String search,
                                                      Pageable pageable);
    
    /**
     * Compte les utilisateurs actifs d'une entreprise
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.company.id = :companyId " +
           "AND u.isDeleted = false AND (u.status = 'Actif' OR u.status IS NULL)")
    Long countActiveUsersByCompanyId(@Param("companyId") Long companyId);
    
    /**
     * Récupère les utilisateurs des entreprises données avec rôle et company chargés (évite N+1 sur la liste entreprises).
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role JOIN FETCH u.company WHERE u.company.id IN :companyIds AND u.isDeleted = false")
    List<User> findByCompanyIdInAndIsDeletedFalseWithRole(@Param("companyIds") List<Long> companyIds);

    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.company.id = :companyId " +
           "AND u.isDeleted = false AND u.role.code = 'ADMIN_ENTREPRISE' " +
           "AND u.email IS NOT NULL")
    List<User> findCompanyAdminsByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.isDeleted = false " +
           "AND u.role.code = 'SUPER_ADMIN' AND u.email IS NOT NULL")
    List<User> findActiveSuperAdmins();
}
