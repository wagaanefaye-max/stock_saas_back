package com.stocksaas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpgradeSubscriptionRequest {

    private String planCode;

    @NotBlank(message = "La durée d'abonnement est obligatoire")
    private String durationCode;
}
