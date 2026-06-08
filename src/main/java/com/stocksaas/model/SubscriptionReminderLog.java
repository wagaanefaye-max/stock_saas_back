package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Journal des rappels e-mail envoyés avant la fin d'abonnement / essai.
 */
@Entity
@Table(
        name = "td_subscription_reminders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscription_reminder",
                columnNames = {"company_id", "reminder_type", "period_end_at"}
        ),
        indexes = {
                @Index(name = "idx_sub_reminder_company", columnList = "company_id")
        }
)
@Data
public class SubscriptionReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** DAYS_7 ou DAYS_1 */
    @Column(name = "reminder_type", nullable = false, length = 10)
    private String reminderType;

    /** Date de fin d'abonnement / essai concernée par le rappel */
    @Column(name = "period_end_at", nullable = false)
    private LocalDateTime periodEndAt;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "sent_to_email", nullable = false, length = 255)
    private String sentToEmail;
}
