package com.stocksaas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour créer une nouvelle catégorie de produit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryRequest {

    @NotBlank(message = "Le code de la catégorie est obligatoire")
    private String code;

    @NotBlank(message = "Le libellé de la catégorie est obligatoire")
    private String label;
}
