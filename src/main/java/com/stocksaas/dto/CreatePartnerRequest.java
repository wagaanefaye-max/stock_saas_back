package com.stocksaas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePartnerRequest {

    @NotBlank(message = "Le rôle est obligatoire")
    @Pattern(regexp = "CLIENT|FOURNISSEUR", message = "Le rôle doit être CLIENT ou FOURNISSEUR")
    private String role;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String phone;

    private String address;

    private String description;
}
