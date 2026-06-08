package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Historique des souscriptions d'une entreprise.
 */
@Entity
@Table(name = "td_company_subscription_records", indexes = {
        @Index(name = "idx_sub_records_company", columnList = "company_id"),
        @Index(name = "idx_sub_records_created", columnList = "created_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "company")
public class CompanySubscriptionRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "plan_label", nullable = false, length = 100)
    private String planLabel;

    @Column(name = "duration_code", nullable = false, length = 20)
    private String durationCode;

    @Column(name = "duration_label", nullable = false, length = 100)
    private String durationLabel;

    @Column(name = "months", nullable = false)
    private Integer months;

    @Column(name = "amount_paid", nullable = false)
    private Double amountPaid;

    /** PENDING, APPROVED, REJECTED */
    @Column(name = "request_status", length = 20, nullable = false)
    private String requestStatus;

    /** WAVE, ORANGE_MONEY */
    @Column(name = "payment_provider", length = 20)
    private String paymentProvider;

    @Column(name = "proof_file_path", length = 500)
    private String proofFilePath;

    /** Renseigné à la validation admin (null si PENDING). */
    @Column(name = "period_start", nullable = true)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = true)
    private LocalDateTime periodEnd;

    @Column(name = "subscribed_by_email", length = 255)
    private String subscribedByEmail;

    @Column(name = "validated_by_email", length = 255)
    private String validatedByEmail;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
}
