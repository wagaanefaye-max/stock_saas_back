CREATE TABLE IF NOT EXISTS tp_platform_settings (
    id BIGINT PRIMARY KEY,
    subscription_monthly_price_fcfa DOUBLE PRECISION NOT NULL DEFAULT 5000,
    maintenance_mode BOOLEAN NOT NULL DEFAULT FALSE,
    maintenance_message TEXT,
    allow_new_registrations BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO tp_platform_settings (id, subscription_monthly_price_fcfa, maintenance_mode, allow_new_registrations)
VALUES (1, 5000, FALSE, TRUE)
ON CONFLICT (id) DO NOTHING;
