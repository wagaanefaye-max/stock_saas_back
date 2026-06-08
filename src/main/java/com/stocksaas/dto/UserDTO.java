package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour représenter un utilisateur dans les listes
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private String roleCode;
    private String roleLabel;
    private String status;
    private Long companyId;
    private String companyName;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
}
