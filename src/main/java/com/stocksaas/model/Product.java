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
 * Table dynamique : Catalogue de produits
 */
@Entity
@Table(name = "td_products", indexes = {
    @Index(name = "idx_products_company_id", columnList = "company_id"),
    @Index(name = "idx_products_sku", columnList = "sku"),
    @Index(name = "idx_products_reference", columnList = "reference"),
    @Index(name = "idx_products_category_code", columnList = "category_code"),
    @Index(name = "idx_products_status", columnList = "status_code"),
    @Index(name = "idx_products_is_deleted", columnList = "is_deleted")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_products_company_sku", columnNames = {"company_id", "sku"}),
    @UniqueConstraint(name = "uk_products_reference", columnNames = {"reference"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"company", "stockLevels", "movements"})
public class Product extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id", nullable = false)
    private Company company;
    
    @NotBlank
    @Column(name = "name", nullable = false, length = 30)
    private String name;
    
    @NotBlank
    @Column(name = "sku", nullable = false, length = 100)
    private String sku; // Stock Keeping Unit

    /** Référence unique explicite : REF_YYYYMMDD_SKU_ID (ex: REF_20260213_TELSAMGAL01_1) */
    @Column(name = "reference", unique = true, length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_code", referencedColumnName = "code")
    private ProductCategory productCategory;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Min(0)
    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price = BigDecimal.ZERO; // Prix en FCFA
    
    @Min(0)
    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO; // Prix d'achat en FCFA
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code")
    private ProductStatus status;
    
    // Relations
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockLevel> stockLevels = new ArrayList<>();
    
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Movement> movements = new ArrayList<>();
}
