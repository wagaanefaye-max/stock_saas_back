package com.stocksaas.repository;

import com.stocksaas.model.Partner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnerRepository extends JpaRepository<Partner, Long> {

    @Query("SELECT p FROM Partner p WHERE p.company.id = :companyId AND p.isDeleted = false ORDER BY p.role, p.name")
    List<Partner> findByCompanyIdAndNotDeleted(@Param("companyId") Long companyId);

    @Query("SELECT p FROM Partner p WHERE p.company.id = :companyId AND p.isDeleted = false ORDER BY p.role, p.name")
    Page<Partner> findByCompanyIdAndNotDeleted(@Param("companyId") Long companyId, Pageable pageable);

    @Query("SELECT p FROM Partner p WHERE p.company.id = :companyId AND p.isDeleted = false " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT(CONCAT('%', :search), '%')) " +
           "OR LOWER(COALESCE(p.email, '')) LIKE LOWER(CONCAT(CONCAT('%', :search), '%'))) ORDER BY p.role, p.name")
    Page<Partner> findByCompanyIdAndNotDeletedAndSearch(@Param("companyId") Long companyId, @Param("search") String search, Pageable pageable);

    @Query("SELECT p FROM Partner p LEFT JOIN FETCH p.company WHERE p.id = :id")
    java.util.Optional<Partner> findByIdWithCompany(@Param("id") Long id);

    @Query("SELECT p FROM Partner p WHERE p.company.id = :companyId AND p.role = :role AND p.isDeleted = false ORDER BY p.name")
    List<Partner> findByCompanyIdAndRoleAndNotDeleted(@Param("companyId") Long companyId, @Param("role") String role);

    @Query("SELECT p FROM Partner p WHERE p.company.id = :companyId AND p.role = :role AND p.isDeleted = false ORDER BY p.name")
    Page<Partner> findByCompanyIdAndRoleAndNotDeleted(@Param("companyId") Long companyId, @Param("role") String role, Pageable pageable);

    /** Pagination avec recherche sur nom ou email (insensible à la casse). */
    @Query("SELECT p FROM Partner p WHERE p.company.id = :companyId AND p.role = :role AND p.isDeleted = false " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT(CONCAT('%', :search), '%')) " +
           "OR LOWER(COALESCE(p.email, '')) LIKE LOWER(CONCAT(CONCAT('%', :search), '%'))) ORDER BY p.name")
    Page<Partner> findByCompanyIdAndRoleAndNotDeletedAndSearch(
            @Param("companyId") Long companyId, @Param("role") String role, @Param("search") String search, Pageable pageable);

    /** Un partenaire (non supprimé) avec cet email existe déjà dans l'entreprise ? (excludeId = null en création, id du partenaire en modification) */
    @Query("SELECT p FROM Partner p WHERE p.company.id = :companyId AND p.isDeleted = false AND LOWER(TRIM(p.email)) = LOWER(TRIM(:email)) AND p.email IS NOT NULL AND p.email <> '' AND (:excludeId IS NULL OR p.id <> :excludeId)")
    java.util.Optional<Partner> findByCompanyIdAndEmailIgnoreCaseAndNotDeleted(@Param("companyId") Long companyId, @Param("email") String email, @Param("excludeId") Long excludeId);

    /** Un partenaire (non supprimé) avec ce téléphone existe déjà dans l'entreprise ? (excludeId = null en création, id en modification) */
    @Query("SELECT p FROM Partner p WHERE p.company.id = :companyId AND p.isDeleted = false AND TRIM(p.phone) = TRIM(:phone) AND p.phone IS NOT NULL AND p.phone <> '' AND (:excludeId IS NULL OR p.id <> :excludeId)")
    java.util.Optional<Partner> findByCompanyIdAndPhoneAndNotDeleted(@Param("companyId") Long companyId, @Param("phone") String phone, @Param("excludeId") Long excludeId);
}
