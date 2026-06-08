package com.stocksaas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Table de référence : durées de facturation d'abonnement.
 */
@Entity
@Table(name = "tp_subscription_duration")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDuration {

    @Id
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "months", nullable = false)
    private Integer months;

    /** Réduction en pourcentage (ex. 5 = 5 %). */
    @Column(name = "discount_percent")
    private Double discountPercent = 0.0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
