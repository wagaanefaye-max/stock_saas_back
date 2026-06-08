package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour représenter un type de mouvement
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovementTypeDTO {
    private String code;
    private String label;
    private String description;
    private Boolean allowsNegative;
    private Boolean requiresDestination;
    private Boolean isActive;
}
