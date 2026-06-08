package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Table de référence : Rôles utilisateurs
 */
@Entity
@Table(name = "tp_user_role")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {
    
    @Id
    @Column(name = "code", length = 50)
    private String code; // SUPER_ADMIN, ADMIN_ENTREPRISE, GESTIONNAIRE, UTILISATEUR
    
    @Column(name = "label", nullable = false, length = 100)
    private String label;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
