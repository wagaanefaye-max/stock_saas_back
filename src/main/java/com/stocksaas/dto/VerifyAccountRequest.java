package com.stocksaas.dto;

import com.stocksaas.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO pour la validation de compte et définition du mot de passe
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyAccountRequest {
    
    @NotBlank(message = "Le token est obligatoire")
    private String token;
    
    @NotBlank(message = "Le mot de passe est obligatoire")
    @StrongPassword
    private String password;

    /** Confirmation du mot de passe (obligatoire côté front, vérifiée en backend) */
    private String passwordConfirmation;
}
