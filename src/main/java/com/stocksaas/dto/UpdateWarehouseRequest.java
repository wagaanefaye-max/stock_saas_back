package com.stocksaas.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la mise à jour d'un entrepôt
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWarehouseRequest {
    
    @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères")
    private String name;

    @Size(max = 100, message = "La région ne peut pas dépasser 100 caractères")
    private String region;

    private String description;

    private String statusCode;
}
