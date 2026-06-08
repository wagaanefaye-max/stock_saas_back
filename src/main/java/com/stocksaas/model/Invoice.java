package com.stocksaas.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Facture émise pour un client (Partner avec rôle CLIENT).
 */
@Entity
@Table(name = "td_invoices", indexes = {
    @Index(name = "idx_invoices_company_id", columnList = "company_id"),
    @Index(name = "idx_invoices_client_id", columnList = "client_id"),
    @Index(name = "idx_invoices_created_by_id", columnList = "created_by_id"),
    @Index(name = "idx_invoices_number", columnList = "invoice_number"),
    @Index(name = "idx_invoices_status", columnList = "status"),
    @Index(name = "idx_invoices_is_deleted", columnList = "is_deleted")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"company", "client", "createdBy", "lines"})
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", referencedColumnName = "id", nullable = false)
    private Partner client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", referencedColumnName = "id")
    private User createdBy;

    @NotBlank
    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @NotNull
    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** DRAFT, SENT, PAID, CANCELLED */
    @NotBlank
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @DecimalMin("0")
    @Column(name = "subtotal", precision = 15, scale = 2, nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @DecimalMin("0")
    @Column(name = "tax_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @DecimalMin("0")
    @Column(name = "total", precision = 15, scale = 2, nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "currency", length = 10)
    private String currency = "FCFA";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<InvoiceLine> lines = new ArrayList<>();
}
