package com.stocksaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour la mise à jour d'un produit
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProductRequest {
    
    @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères")
    private String name;
    
    @Size(max = 100, message = "Le code SKU ne peut pas dépasser 100 caractères")
    private String sku;
    
    @Size(max = 50, message = "Le code catégorie ne peut pas dépasser 50 caractères")
    private String categoryCode;
    
    private String description;
    
    @Min(value = 0, message = "Le prix doit être positif ou nul")
    private BigDecimal price;
    
    @Min(value = 0, message = "Le prix d'achat doit être positif ou nul")
    private BigDecimal purchasePrice;
    
    private Long warehouseId;
    
    @Min(value = 0, message = "Le stock doit être positif ou nul")
    private BigDecimal stock;
    
    @Min(value = 0, message = "La quantité doit être positive ou nulle")
    private BigDecimal quantity;

    @Min(value = 0, message = "Le seuil minimum doit être positif ou nul")
    private BigDecimal minThreshold;
}
