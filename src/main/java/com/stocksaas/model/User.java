package com.stocksaas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Table dynamique : Utilisateurs de la plateforme
 */
@Entity
@Table(name = "td_users", indexes = {
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_company_id", columnList = "company_id"),
    @Index(name = "idx_users_role", columnList = "role_code"),
    @Index(name = "idx_users_status", columnList = "status"),
    @Index(name = "idx_users_is_deleted", columnList = "is_deleted")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"password", "company", "assignedWarehouses", "movements"})
public class User extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id")
    private Company company;
    
    @Email
    @NotBlank
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;
    
    @Column(name = "password", length = 255)
    private String password; // Hashé avec BCrypt (nullable jusqu'à validation du compte)
    
    @NotBlank
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_code", referencedColumnName = "code", nullable = false)
    private UserRole role;
    
    @Column(name = "status", length = 50)
    private String status = "Actif"; // Actif, Inactif (peut être remplacé par une table TP si nécessaire)
    
    // Méthodes helper
    public String getRoleCode() {
        return role != null ? role.getCode() : null;
    }
    
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(getRoleCode());
    }
    
    public boolean isAdminEntreprise() {
        return "ADMIN_ENTREPRISE".equals(getRoleCode());
    }
    
    public boolean isGestionnaire() {
        return "GESTIONNAIRE".equals(getRoleCode());
    }
    
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
    
    // Relations
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserWarehouse> assignedWarehouses = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Movement> movements = new ArrayList<>();
}
