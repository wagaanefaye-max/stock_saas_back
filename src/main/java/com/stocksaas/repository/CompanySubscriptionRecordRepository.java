package com.stocksaas.repository;

import com.stocksaas.model.CompanySubscriptionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanySubscriptionRecordRepository extends JpaRepository<CompanySubscriptionRecord, Long> {

    List<CompanySubscriptionRecord> findByCompanyIdAndIsDeletedFalseOrderByCreatedAtDesc(Long companyId);

    List<CompanySubscriptionRecord> findByRequestStatusAndIsDeletedFalseOrderByCreatedAtDesc(String requestStatus);

    @EntityGraph(attributePaths = {"company"})
    Page<CompanySubscriptionRecord> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"company"})
    Page<CompanySubscriptionRecord> findByRequestStatusAndIsDeletedFalseOrderByCreatedAtDesc(
            String requestStatus, Pageable pageable);

    long countByIsDeletedFalse();

    long countByRequestStatusAndIsDeletedFalse(String requestStatus);

    @Query("SELECT r FROM CompanySubscriptionRecord r JOIN FETCH r.company "
            + "WHERE r.requestStatus = :status AND r.isDeleted = false ORDER BY r.createdAt DESC")
    List<CompanySubscriptionRecord> findByRequestStatusWithCompany(@Param("status") String requestStatus);

    @Query("SELECT r FROM CompanySubscriptionRecord r JOIN FETCH r.company "
            + "WHERE r.isDeleted = false ORDER BY r.createdAt DESC")
    List<CompanySubscriptionRecord> findAllWithCompanyOrderByCreatedAtDesc();

    boolean existsByCompanyIdAndRequestStatusAndIsDeletedFalse(Long companyId, String requestStatus);

    Optional<CompanySubscriptionRecord> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT r FROM CompanySubscriptionRecord r WHERE r.isDeleted = false AND r.createdAt >= :startDate")
    List<CompanySubscriptionRecord> findByCreatedAtAfterAndIsDeletedFalse(@Param("startDate") LocalDateTime startDate);
}
