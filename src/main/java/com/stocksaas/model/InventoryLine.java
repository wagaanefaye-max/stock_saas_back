package com.stocksaas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Ligne d'inventaire : produit + quantité théorique (au moment du comptage) + quantité comptée.
 * L'écart (compté - théorique) est calculé à la validation pour créer les mouvements d'ajustement.
 */
@Entity
@Table(name = "td_inventory_lines", indexes = {
    @Index(name = "idx_inventory_lines_inventory_id", columnList = "inventory_id"),
    @Index(name = "idx_inventory_lines_product_id", columnList = "product_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_line_inventory_product", columnNames = {"inventory_id", "product_id"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"inventory", "product"})
public class InventoryLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", referencedColumnName = "id", nullable = false)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "id", nullable = false)
    private Product product;

    /** Quantité en stock au moment de l'initialisation de la ligne (snapshot). */
    @Min(0)
    @Column(name = "theoretical_quantity", nullable = false, precision = 15, scale = 2)
    private BigDecimal theoreticalQuantity = BigDecimal.ZERO;

    /** Quantité physiquement comptée (null = non encore saisie). */
    @Min(0)
    @Column(name = "counted_quantity", precision = 15, scale = 2)
    private BigDecimal countedQuantity;
}
