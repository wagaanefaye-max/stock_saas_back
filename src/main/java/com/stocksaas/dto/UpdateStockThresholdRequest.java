package com.stocksaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateStockThresholdRequest {

    @NotNull(message = "Le seuil minimum est obligatoire")
    @Min(value = 0, message = "Le seuil minimum doit être positif ou nul")
    private BigDecimal minThreshold;
}
