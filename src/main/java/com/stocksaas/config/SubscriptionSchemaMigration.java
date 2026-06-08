package com.stocksaas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Corrige le schéma PostgreSQL lorsque period_start/period_end sont encore NOT NULL
 * (demandes en attente sans période définie).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionSchemaMigration {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateSubscriptionRecordPeriodColumns() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE td_company_subscription_records ALTER COLUMN period_start DROP NOT NULL");
            jdbcTemplate.execute(
                    "ALTER TABLE td_company_subscription_records ALTER COLUMN period_end DROP NOT NULL");
            log.debug("Schéma abonnements : period_start et period_end acceptent NULL");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("does not exist") || msg.contains("n'existe pas")) {
                log.debug("Table td_company_subscription_records absente, migration ignorée");
            } else if (msg.contains("already") || msg.contains("déjà")) {
                log.debug("Contraintes period_* déjà assouplies");
            } else {
                log.warn("Migration schéma abonnements (period_start/period_end): {}", msg);
            }
        }
    }
}
