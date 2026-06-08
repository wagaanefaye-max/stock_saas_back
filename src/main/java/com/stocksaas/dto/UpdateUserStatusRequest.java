package com.stocksaas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la mise à jour du statut d'un utilisateur
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserStatusRequest {
    @NotBlank(message = "Le statut est obligatoire")
    private String status; // "Actif" ou "Inactif"
}
