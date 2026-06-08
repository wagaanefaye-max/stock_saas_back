package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour représenter une entreprise dans les listes.
 * Le constructeur complet est utilisé par les projections JPQL du repository.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String region;
    private String country;
    private String planCode;
    private String planLabel;
    private String statusCode;
    private String statusLabel;
    private String logoUrl;
    private Long userCount;
    private LocalDateTime createdAt;
    /** Nom de l'administrateur principal (rôle ADMIN_ENTREPRISE) */
    private String adminName;
    /** Email de l'administrateur principal */
    private String adminEmail;
    private Boolean notifLowStock;
    private Boolean notifMovements;
    private Boolean notifReports;
    private String subscriptionStatus;
    private LocalDateTime trialEndsAt;
    private LocalDateTime subscriptionEndsAt;
    private String durationCode;
}
