package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionStatusDTO {

    private Long companyId;
    private String planCode;
    private String planLabel;
    private Double planMonthlyPrice;
    private String subscriptionStatus;
    private String subscriptionStatusLabel;
    private LocalDateTime trialEndsAt;
    private LocalDateTime subscriptionEndsAt;
    private String durationCode;
    private String durationLabel;
    private boolean readOnly;
    private boolean canUpgrade;
    private boolean hasPendingRequest;
    private long daysRemaining;
    /** Date à partir de laquelle une nouvelle souscription sera cumulée (fin de l'abonnement ou essai en cours). */
    private LocalDateTime nextCumulativeStartAt;
    /** true si une souscription en cours sera prolongée (pas de perte de jours restants). */
    private boolean willStackSubscription;
}
