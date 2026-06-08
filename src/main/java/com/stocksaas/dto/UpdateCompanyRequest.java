package com.stocksaas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la mise à jour d'une entreprise
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCompanyRequest {
    @NotBlank(message = "Le nom est obligatoire")
    private String name;
    
    @Email(message = "L'email doit être valide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;
    
    private String phone;
    private String address;
    private String region;
    private String country;
    private String planCode;
    private Boolean notifLowStock;
    private Boolean notifMovements;
    private Boolean notifReports;
}
