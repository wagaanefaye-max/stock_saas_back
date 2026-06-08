package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Table de référence : Plans d'abonnement
 */
@Entity
@Table(name = "tp_subscription_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {
    
    @Id
    @Column(name = "code", length = 50)
    private String code; // Free, Basique, Standard, Premium
    
    @Column(name = "label", nullable = false, length = 100)
    private String label;
    
    @Column(name = "price", nullable = false)
    private Double price; // Prix en FCFA
    
    @Column(name = "max_users")
    private Integer maxUsers;
    
    @Column(name = "max_warehouses")
    private Integer maxWarehouses;
    
    @Column(name = "trial_days")
    private Integer trialDays;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
