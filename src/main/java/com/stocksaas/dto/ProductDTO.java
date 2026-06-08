package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO pour représenter un produit
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDTO {
    private Long id;
    /** Référence unique explicite (ex: REF_20260213_TELSAMGAL01_1) */
    private String reference;
    private String name;
    private String sku;
    /** Libellé de la catégorie (pour affichage) */
    private String category;
    /** Code de la catégorie (pour formulaire) */
    private String categoryCode;
    private String description;
    private BigDecimal price;
    private BigDecimal purchasePrice;
    private String statusCode;
    private String statusLabel;
    private Long warehouseId;
    private String warehouseName;
    private BigDecimal stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
