package com.stocksaas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettingsDTO {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "Le montant mensuel doit être supérieur à 0")
    private Double subscriptionMonthlyPriceFcfa;

    @NotNull
    private Boolean maintenanceMode;

    @NotNull
    private Boolean allowNewRegistrations;
}
