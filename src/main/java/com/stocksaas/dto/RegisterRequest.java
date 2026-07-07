package com.stocksaas.dto;

import com.stocksaas.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO pour la requête d'inscription
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 255, message = "Le nom doit contenir entre 2 et 255 caractères")
    private String name;
    
    @Email(message = "L'email doit être valide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @StrongPassword
    private String password;

    /** Confirmation du mot de passe (vérifiée côté backend) */
    private String passwordConfirmation;
    
    // Informations entreprise (optionnel pour l'inscription express)
    private String companyName;
    private String companyEmail;
    private String companyPhone;
    private String companyAddress;
    private String companyRegion;
    
    // Plan d'abonnement sélectionné
    private String planCode = "Free"; // Par défaut "Free"
}
