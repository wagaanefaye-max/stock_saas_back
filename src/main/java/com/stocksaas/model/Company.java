package com.stocksaas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Table dynamique : Entreprises clientes de la plateforme
 */
@Entity
@Table(name = "td_companies", indexes = {
    @Index(name = "idx_companies_email", columnList = "email"),
    @Index(name = "idx_companies_status", columnList = "status_code"),
    @Index(name = "idx_companies_plan", columnList = "plan_code"),
    @Index(name = "idx_companies_is_deleted", columnList = "is_deleted")
})
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"users", "warehouses", "products", "movements", "partners", "invoices"})
public class Company extends BaseEntity {
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;
    
    @Column(name = "phone", length = 50)
    private String phone;
    
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;
    
    @Column(name = "region", length = 100)
    private String region;
    
    @Column(name = "country", length = 100)
    private String country = "Sénégal";
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_code", referencedColumnName = "code")
    private SubscriptionPlan plan;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_code", referencedColumnName = "code")
    private CompanyStatus status;
    
    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Lob
    @Column(name = "logo_data")
    private byte[] logoData;

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(name = "notif_low_stock")
    private Boolean notifLowStock = true;

    @Column(name = "notif_movements")
    private Boolean notifMovements = true;

    @Column(name = "notif_reports")
    private Boolean notifReports = false;

    /** TRIAL, ACTIVE, EXPIRED */
    @Column(name = "subscription_status", length = 20)
    private String subscriptionStatus;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(name = "subscription_ends_at")
    private LocalDateTime subscriptionEndsAt;

    @Column(name = "duration_code", length = 20)
    private String durationCode;
    
    // Relations
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<User> users = new ArrayList<>();
    
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Warehouse> warehouses = new ArrayList<>();
    
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();
    
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Movement> movements = new ArrayList<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Partner> partners = new ArrayList<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Invoice> invoices = new ArrayList<>();
    
    // Méthodes helper pour accéder aux codes
    public String getPlanCode() {
        return plan != null ? plan.getCode() : "Free";
    }
    
    public String getStatusCode() {
        return status != null ? status.getCode() : "Actif";
    }
}
