-- Journal des rappels e-mail avant fin d'abonnement / essai
CREATE TABLE IF NOT EXISTS td_subscription_reminders (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    reminder_type VARCHAR(10) NOT NULL,
    period_end_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    sent_to_email VARCHAR(255) NOT NULL,
    CONSTRAINT uk_subscription_reminder UNIQUE (company_id, reminder_type, period_end_at)
);

CREATE INDEX IF NOT EXISTS idx_sub_reminder_company ON td_subscription_reminders (company_id);
