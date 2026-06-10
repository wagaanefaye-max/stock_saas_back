package com.stocksaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour la création d'un produit
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductRequest {
    
    @NotBlank(message = "Le nom du produit est obligatoire")
    @Size(max = 255, message = "Le nom ne peut pas dépasser 255 caractères")
    private String name;
    
    /** Code de la catégorie (référence tp_category) */
    @Size(max = 50, message = "Le code catégorie ne peut pas dépasser 50 caractères")
    private String categoryCode;
    
    private String description;
    
    @Min(value = 0, message = "Le prix doit être positif ou nul")
    private BigDecimal price;
    
    @Min(value = 0, message = "Le prix d'achat doit être positif ou nul")
    private BigDecimal purchasePrice;
    
    /** Entrepôt optionnel : affecte le produit à cet entrepôt (création d'un StockLevel). */
    private Long warehouseId;
    
    @Min(value = 0, message = "La quantité doit être positive ou nulle")
    private BigDecimal quantity;

    @Min(value = 0, message = "Le seuil minimum doit être positif ou nul")
    private BigDecimal minThreshold;
}
