package com.stocksaas.repository;

import com.stocksaas.model.CompanySubscriptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanySubscriptionRecordRepository extends JpaRepository<CompanySubscriptionRecord, Long> {

    List<CompanySubscriptionRecord> findByCompanyIdAndIsDeletedFalseOrderByCreatedAtDesc(Long companyId);

    List<CompanySubscriptionRecord> findByRequestStatusAndIsDeletedFalseOrderByCreatedAtDesc(String requestStatus);

    @Query("SELECT r FROM CompanySubscriptionRecord r JOIN FETCH r.company "
            + "WHERE r.requestStatus = :status AND r.isDeleted = false ORDER BY r.createdAt DESC")
    List<CompanySubscriptionRecord> findByRequestStatusWithCompany(@Param("status") String requestStatus);

    @Query("SELECT r FROM CompanySubscriptionRecord r JOIN FETCH r.company "
            + "WHERE r.isDeleted = false ORDER BY r.createdAt DESC")
    List<CompanySubscriptionRecord> findAllWithCompanyOrderByCreatedAtDesc();

    boolean existsByCompanyIdAndRequestStatusAndIsDeletedFalse(Long companyId, String requestStatus);

    Optional<CompanySubscriptionRecord> findByIdAndIsDeletedFalse(Long id);
}
