package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour un produit présent dans un entrepôt avec sa quantité
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInWarehouseDTO {
    private Long productId;
    /** Référence unique du produit (ex: REF_20260213_TELSAMGAL01_1) */
    private String productReference;
    private String productName;
    private String sku;
    /** Libellé de la catégorie du produit */
    private String category;
    private BigDecimal price;
    /** Quantité de ce produit dans l'entrepôt */
    private BigDecimal quantity;
    private BigDecimal minThreshold;
    /** Vrai si seuil min > 0 et quantité ≤ seuil */
    private Boolean lowStock;
}
