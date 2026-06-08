-- Permet les demandes PENDING sans dates de période (renseignées à la validation admin)
ALTER TABLE td_company_subscription_records
    ALTER COLUMN period_start DROP NOT NULL;

ALTER TABLE td_company_subscription_records
    ALTER COLUMN period_end DROP NOT NULL;
