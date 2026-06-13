package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    /** Détail du stock par entrepôt (quantité > 0 ou seuil défini) */
    private List<ProductWarehouseStockDTO> warehouseStocks;
    /** Seuil minimum (entrepôt principal affiché) */
    private BigDecimal minThreshold;
    /** Vrai si au moins un entrepôt a quantité ≤ seuil min (> 0) */
    private Boolean lowStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
