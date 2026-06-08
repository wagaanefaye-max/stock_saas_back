package com.stocksaas.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInventoryRequest {

    @NotNull(message = "L'entrepôt est obligatoire")
    private Long warehouseId;

    @NotNull(message = "La date d'inventaire est obligatoire")
    private LocalDate inventoryDate;

    private String notes;
}
