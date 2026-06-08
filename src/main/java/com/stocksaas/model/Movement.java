package com.stocksaas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Table dynamique : Historique des mouvements de stock
 */
@Entity
@Table(name = "td_movements", indexes = {
    @Index(name = "idx_movements_company_id", columnList = "company_id"),
    @Index(name = "idx_movements_product_id", columnList = "product_id"),
    @Index(name = "idx_movements_warehouse_id", columnList = "warehouse_id"),
    @Index(name = "idx_movements_dest_warehouse_id", columnList = "dest_warehouse_id"),
    @Index(name = "idx_movements_user_id", columnList = "user_id"),
    @Index(name = "idx_movements_type", columnList = "type_code"),
    @Index(name = "idx_movements_date", columnList = "date"),
    @Index(name = "idx_movements_created_at", columnList = "created_at"),
    @Index(name = "idx_movements_is_deleted", columnList = "is_deleted"),
    @Index(name = "idx_movements_company_date", columnList = "company_id, date"),
    @Index(name = "idx_movements_warehouse_date", columnList = "warehouse_id, date")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"company", "product", "warehouse", "destinationWarehouse", "user", "type"})
public class Movement extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id", nullable = false)
    private Company company;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "id", nullable = false)
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", referencedColumnName = "id", nullable = false)
    private Warehouse warehouse;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_warehouse_id", referencedColumnName = "id")
    private Warehouse destinationWarehouse;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_code", referencedColumnName = "code", nullable = false)
    private MovementType type;
    
    @NotNull
    @Column(name = "quantity", nullable = false, precision = 15, scale = 2)
    private BigDecimal quantity;
    
    @NotNull
    @Column(name = "date", nullable = false)
    private LocalDate date = LocalDate.now();
    
    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
