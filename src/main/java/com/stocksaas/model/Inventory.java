package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Session d'inventaire par entrepôt (traçabilité).
 * Statuts : DRAFT, IN_PROGRESS, CLOSED.
 */
@Entity
@Table(name = "td_inventories", indexes = {
    @Index(name = "idx_inventories_company_id", columnList = "company_id"),
    @Index(name = "idx_inventories_warehouse_id", columnList = "warehouse_id"),
    @Index(name = "idx_inventories_status", columnList = "status"),
    @Index(name = "idx_inventories_inventory_date", columnList = "inventory_date")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"company", "warehouse", "createdBy"})
public class Inventory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", referencedColumnName = "id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "inventory_date", nullable = false)
    private LocalDate inventoryDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT"; // DRAFT, IN_PROGRESS, CLOSED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", referencedColumnName = "id")
    private User createdBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
