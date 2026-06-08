package com.stocksaas.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * DTO pour la réponse d'authentification
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    
    private String token;
    private String type = "Bearer";
    private String email;
    private String name;
    private String role;
    private Long companyId;
    private String companyName;
    private String planCode;
    private String subscriptionStatus;
    private String subscriptionStatusLabel;
    private LocalDateTime trialEndsAt;
    private LocalDateTime subscriptionEndsAt;
    private Boolean readOnly;
    private Long daysRemaining;
}
