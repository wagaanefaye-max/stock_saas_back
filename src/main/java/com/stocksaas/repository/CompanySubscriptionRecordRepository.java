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

    @Query("SELECT COALESCE(SUM(r.amountPaid), 0) FROM CompanySubscriptionRecord r "
            + "WHERE r.isDeleted = false AND r.requestStatus = :status "
            + "AND r.validatedAt IS NOT NULL AND r.validatedAt >= :start AND r.validatedAt < :end")
    Double sumAmountPaidByStatusValidatedBetween(@Param("status") String status,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(r) FROM CompanySubscriptionRecord r "
            + "WHERE r.isDeleted = false AND r.requestStatus = :status "
            + "AND r.createdAt >= :start AND r.createdAt < :end")
    long countByStatusCreatedBetween(@Param("status") String status,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Query(value = "SELECT EXTRACT(YEAR FROM r.created_at)::int, EXTRACT(MONTH FROM r.created_at)::int, "
            + "r.request_status, COUNT(r.id) "
            + "FROM td_company_subscription_records r "
            + "WHERE r.created_at >= CAST(:startDate AS timestamp) AND r.is_deleted = false "
            + "GROUP BY EXTRACT(YEAR FROM r.created_at), EXTRACT(MONTH FROM r.created_at), r.request_status",
            nativeQuery = true)
    List<Object[]> countSubscriptionsByYearMonthAndStatus(@Param("startDate") LocalDateTime startDate);

    @Query(value = "SELECT "
            + "COALESCE(SUM(CASE WHEN r.validated_at >= :currentStart AND r.validated_at < :currentEnd "
            + "THEN r.amount_paid ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN r.validated_at >= :prevStart AND r.validated_at < :prevEnd "
            + "THEN r.amount_paid ELSE 0 END), 0) "
            + "FROM td_company_subscription_records r "
            + "WHERE r.is_deleted = false AND r.request_status = 'APPROVED' AND r.validated_at IS NOT NULL "
            + "AND r.validated_at >= :prevStart AND r.validated_at < :currentEnd", nativeQuery = true)
    Object[] sumApprovedRevenueCurrentAndPrevious(@Param("currentStart") LocalDateTime currentStart,
                                                  @Param("currentEnd") LocalDateTime currentEnd,
                                                  @Param("prevStart") LocalDateTime prevStart,
                                                  @Param("prevEnd") LocalDateTime prevEnd);

    @Query(value = "SELECT "
            + "COALESCE(SUM(CASE WHEN r.created_at >= :currentStart AND r.created_at < :currentEnd THEN 1 ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN r.created_at >= :prevStart AND r.created_at < :prevEnd THEN 1 ELSE 0 END), 0) "
            + "FROM td_company_subscription_records r "
            + "WHERE r.is_deleted = false AND r.request_status = 'PENDING'", nativeQuery = true)
    Object[] countPendingSubscriptionsCurrentAndPrevious(@Param("currentStart") LocalDateTime currentStart,
                                                         @Param("currentEnd") LocalDateTime currentEnd,
                                                         @Param("prevStart") LocalDateTime prevStart,
                                                         @Param("prevEnd") LocalDateTime prevEnd);
}
