package com.stocksaas.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    
    @NotNull(message = "Le prix de vente est obligatoire")
    @DecimalMin(value = "0.01", inclusive = false, message = "Le prix de vente doit être supérieur à 0")
    private BigDecimal price;
    
    @NotNull(message = "Le prix d'achat est obligatoire")
    @DecimalMin(value = "0.01", inclusive = false, message = "Le prix d'achat doit être supérieur à 0")
    private BigDecimal purchasePrice;
    
    /** Entrepôt optionnel : affecte le produit à cet entrepôt (création d'un StockLevel). */
    private Long warehouseId;
    
    @NotNull(message = "La quantité initiale est obligatoire")
    @DecimalMin(value = "1", inclusive = true, message = "La quantité initiale doit être supérieure à 0")
    private BigDecimal quantity;

    @NotNull(message = "Le seuil minimum est obligatoire")
    @DecimalMin(value = "1", inclusive = true, message = "Le seuil minimum doit être supérieur à 0")
    private BigDecimal minThreshold;
}
