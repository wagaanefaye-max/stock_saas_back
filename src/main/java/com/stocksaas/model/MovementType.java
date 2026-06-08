package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Table de référence : Types de mouvements
 */
@Entity
@Table(name = "tp_movement_type")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovementType {
    
    @Id
    @Column(name = "code", length = 50)
    private String code; // ENTREE, SORTIE, TRANSFERT, AJUSTEMENT
    
    @Column(name = "label", nullable = false, length = 100)
    private String label;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "allows_negative", nullable = false)
    private Boolean allowsNegative = false; // Pour Ajustement
    
    @Column(name = "requires_destination", nullable = false)
    private Boolean requiresDestination = false; // Pour Transfert
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
