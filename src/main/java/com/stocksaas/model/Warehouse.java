package com.stocksaas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Table dynamique : Entrepôts de stockage
 */
@Entity
@Table(name = "td_warehouses", indexes = {
    @Index(name = "idx_warehouses_company_id", columnList = "company_id"),
    @Index(name = "idx_warehouses_region", columnList = "region"),
    @Index(name = "idx_warehouses_status", columnList = "status_code"),
    @Index(name = "idx_warehouses_is_deleted", columnList = "is_deleted")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"company", "assignedUsers", "products", "stockLevels", "movements", "destinationMovements"})
public class Warehouse extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id", nullable = false)
    private Company company;
    
    @NotBlank
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @NotBlank
    @Column(name = "region", nullable = false, length = 100)
    private String region;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Conservé pour compatibilité schéma DB (NOT NULL), non utilisé dans le métier */
    @Min(0)
    @Column(name = "capacity", nullable = false, precision = 15, scale = 2)
    private BigDecimal capacity = BigDecimal.ZERO;

    /** Conservé pour compatibilité schéma DB (NOT NULL), non utilisé dans le métier */
    @Min(0)
    @Column(name = "used", nullable = false, precision = 15, scale = 2)
    private BigDecimal used = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code")
    private WarehouseStatus status;
    
    // Relations
    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserWarehouse> assignedUsers = new ArrayList<>();
    
    // Note: Les produits n'appartiennent plus à un entrepôt spécifique
    // Ils appartiennent à l'entreprise, seul le stock est géré par entrepôt via StockLevel
    
    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockLevel> stockLevels = new ArrayList<>();
    
    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Movement> movements = new ArrayList<>();
    
    @OneToMany(mappedBy = "destinationWarehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Movement> destinationMovements = new ArrayList<>();
}
