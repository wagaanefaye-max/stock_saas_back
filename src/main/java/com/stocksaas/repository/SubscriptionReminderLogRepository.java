package com.stocksaas.repository;

import com.stocksaas.model.SubscriptionReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SubscriptionReminderLogRepository extends JpaRepository<SubscriptionReminderLog, Long> {

    boolean existsByCompanyIdAndReminderTypeAndPeriodEndAt(
            Long companyId,
            String reminderType,
            LocalDateTime periodEndAt
    );
}
