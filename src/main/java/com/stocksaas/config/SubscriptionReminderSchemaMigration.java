package com.stocksaas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionReminderSchemaMigration {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureReminderTable() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS td_subscription_reminders (
                        id BIGSERIAL PRIMARY KEY,
                        company_id BIGINT NOT NULL,
                        reminder_type VARCHAR(10) NOT NULL,
                        period_end_at TIMESTAMP NOT NULL,
                        sent_at TIMESTAMP NOT NULL,
                        sent_to_email VARCHAR(255) NOT NULL,
                        CONSTRAINT uk_subscription_reminder UNIQUE (company_id, reminder_type, period_end_at)
                    )
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_sub_reminder_company ON td_subscription_reminders (company_id)");
            log.debug("Table td_subscription_reminders prête");
        } catch (Exception e) {
            log.warn("Migration td_subscription_reminders: {}", e.getMessage());
        }
    }
}
