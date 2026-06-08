package com.stocksaas.dto;

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
public class UpdatePartnerRequest {

    @Pattern(regexp = "CLIENT|FOURNISSEUR", message = "Le rôle doit être CLIENT ou FOURNISSEUR")
    private String role;

    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String email;

    @Size(max = 50)
    private String phone;

    private String address;

    private String description;
}
