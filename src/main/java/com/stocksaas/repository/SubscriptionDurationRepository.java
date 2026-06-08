package com.stocksaas.repository;

import com.stocksaas.model.SubscriptionDuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionDurationRepository extends JpaRepository<SubscriptionDuration, String> {

    List<SubscriptionDuration> findByIsActiveTrueOrderByMonthsAsc();
}
