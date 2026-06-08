package com.stocksaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO pour la création d'un mouvement de stock
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMovementRequest {
    
    @NotNull(message = "Le type de mouvement est obligatoire")
    @Size(max = 50, message = "Le type ne peut pas dépasser 50 caractères")
    private String typeCode; // ENTREE, SORTIE, TRANSFERT, AJUSTEMENT
    
    @NotNull(message = "Le produit est obligatoire")
    private Long productId;
    
    @NotNull(message = "La quantité est obligatoire")
    private BigDecimal quantity;
    
    @NotNull(message = "La date est obligatoire")
    private LocalDate date;
    
    private Long warehouseId;
    
    private Long destinationWarehouseId; // Requis pour Transfert
    
    @Size(max = 1000, message = "La justification ne peut pas dépasser 1000 caractères")
    private String justification;
}
