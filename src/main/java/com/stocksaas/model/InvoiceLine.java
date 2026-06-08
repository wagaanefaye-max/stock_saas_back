package com.stocksaas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Ligne de facture : produit, quantité, prix unitaire, montant.
 */
@Entity
@Table(name = "td_invoice_lines", indexes = {
    @Index(name = "idx_invoice_lines_invoice_id", columnList = "invoice_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"invoice", "product"})
public class InvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", referencedColumnName = "id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;

    /** Libellé (nom produit ou description libre) */
    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @NotNull
    @DecimalMin("0.0001")
    @Column(name = "quantity", precision = 15, scale = 4, nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @NotNull
    @DecimalMin("0")
    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0")
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;
}
