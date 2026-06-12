package com.stocksaas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paramètres globaux de la plateforme (ligne unique id = 1).
 */
@Entity
@Table(name = "tp_platform_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id")
    private Long id = SINGLETON_ID;

    @Column(name = "subscription_monthly_price_fcfa", nullable = false)
    private Double subscriptionMonthlyPriceFcfa = 5000.0;

    @Column(name = "maintenance_mode", nullable = false)
    private Boolean maintenanceMode = false;

    @Column(name = "maintenance_message", columnDefinition = "TEXT")
    private String maintenanceMessage;

    @Column(name = "allow_new_registrations", nullable = false)
    private Boolean allowNewRegistrations = true;
}
