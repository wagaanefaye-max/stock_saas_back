package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerDTO {
    private Long id;
    private String role;       // CLIENT, FOURNISSEUR
    private String roleLabel;  // Client, Fournisseur
    private String name;
    private String email;
    private String phone;
    private String address;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
