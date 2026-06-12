package com.stocksaas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettingsDTO {

    @NotNull(message = "Le montant mensuel est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le montant mensuel doit être supérieur à 0")
    private Double subscriptionMonthlyPriceFcfa;

    @NotNull(message = "Le mode maintenance est obligatoire")
    private Boolean maintenanceMode;

    @Size(max = 1000, message = "Le motif de maintenance ne peut pas dépasser 1000 caractères")
    private String maintenanceMessage;

    @NotNull(message = "L'option d'inscription est obligatoire")
    private Boolean allowNewRegistrations;
}
