package com.stocksaas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Table dynamique : Niveaux de stock par produit et par entrepôt
 */
@Entity
@Table(name = "td_stock_levels", indexes = {
    @Index(name = "idx_stock_levels_product_id", columnList = "product_id"),
    @Index(name = "idx_stock_levels_warehouse_id", columnList = "warehouse_id"),
    @Index(name = "idx_stock_levels_quantity", columnList = "quantity")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_stock_levels_product_warehouse", columnNames = {"product_id", "warehouse_id"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"product", "warehouse"})
public class StockLevel extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "id", nullable = false)
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", referencedColumnName = "id", nullable = false)
    private Warehouse warehouse;
    
    @Min(0)
    @Column(name = "quantity", nullable = false, precision = 15, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;
    
    @Min(0)
    @Column(name = "min_threshold", precision = 15, scale = 2)
    private BigDecimal minThreshold = BigDecimal.ZERO;
    
    @Column(name = "max_threshold", precision = 15, scale = 2)
    private BigDecimal maxThreshold;
}
