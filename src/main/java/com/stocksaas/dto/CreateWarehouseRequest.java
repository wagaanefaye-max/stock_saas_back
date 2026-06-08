package com.stocksaas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la création d'un entrepôt
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWarehouseRequest {
    
    @NotBlank(message = "Le nom de l'entrepôt est obligatoire")
    @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères")
    private String name;

    @NotBlank(message = "La région est obligatoire")
    @Size(max = 100, message = "La région ne peut pas dépasser 100 caractères")
    private String region;

    private String description;

    private String statusCode;
}
