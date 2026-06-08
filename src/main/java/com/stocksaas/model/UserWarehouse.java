package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Table de relation : Liaison entre utilisateurs et entrepôts
 */
@Entity
@Table(name = "tr_user_warehouses", indexes = {
    @Index(name = "idx_user_warehouses_user_id", columnList = "user_id"),
    @Index(name = "idx_user_warehouses_warehouse_id", columnList = "warehouse_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserWarehouseId.class)
public class UserWarehouse {
    
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;
    
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", referencedColumnName = "id", nullable = false)
    private Warehouse warehouse;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
