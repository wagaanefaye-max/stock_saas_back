package com.stocksaas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la création d'une entreprise (boutique) par le Super Admin.
 * Après création, un email d'activation est envoyé à l'administrateur.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCompanyRequest {

    @NotBlank(message = "Le nom de l'entreprise est obligatoire")
    private String name;

    @Email(message = "L'email de l'entreprise doit être valide")
    @NotBlank(message = "L'email de l'entreprise est obligatoire")
    private String email;

    private String phone;
    private String address;
    private String region;
    private String country;
    private String planCode;

    @NotBlank(message = "Le prénom de l'administrateur est obligatoire")
    private String adminFirstName;

    @NotBlank(message = "Le nom de l'administrateur est obligatoire")
    private String adminLastName;

    @Email(message = "L'email de l'administrateur doit être valide")
    @NotBlank(message = "L'email de l'administrateur est obligatoire")
    private String adminEmail;
}
