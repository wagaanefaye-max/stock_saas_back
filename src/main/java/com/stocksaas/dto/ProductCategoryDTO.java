package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour une catégorie de produit (liste déroulante)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategoryDTO {
    private String code;
    private String label;
}
