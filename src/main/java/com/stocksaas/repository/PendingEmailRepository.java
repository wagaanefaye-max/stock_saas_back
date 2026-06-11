package com.stocksaas.repository;

import com.stocksaas.model.PendingEmail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PendingEmailRepository extends JpaRepository<PendingEmail, Long> {

    @Query("""
            SELECT e FROM PendingEmail e
            WHERE e.status = :status
              AND e.isDeleted = false
              AND e.nextRetryAt <= :now
            ORDER BY e.nextRetryAt ASC
            """)
    List<PendingEmail> findDueForRetry(
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
