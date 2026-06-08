package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Table de référence : Statuts des entreprises
 */
@Entity
@Table(name = "tp_company_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyStatus {
    
    @Id
    @Column(name = "code", length = 50)
    private String code; // Actif, Inactif, Suspendu
    
    @Column(name = "label", nullable = false, length = 100)
    private String label;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
