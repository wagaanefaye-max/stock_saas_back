package com.stocksaas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Partenaires de l'entreprise : clients et fournisseurs (sans compte utilisateur).
 * Rôle : CLIENT ou FOURNISSEUR.
 */
@Entity
@Table(name = "td_partners", indexes = {
    @Index(name = "idx_partners_company_id", columnList = "company_id"),
    @Index(name = "idx_partners_role", columnList = "role"),
    @Index(name = "idx_partners_is_deleted", columnList = "is_deleted")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "company")
public class Partner extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id", nullable = false)
    private Company company;

    /** CLIENT ou FOURNISSEUR */
    @NotBlank
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @NotBlank
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
