package com.stocksaas.repository;

import com.stocksaas.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour l'entité SubscriptionPlan (table de référence)
 */
@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {
}
