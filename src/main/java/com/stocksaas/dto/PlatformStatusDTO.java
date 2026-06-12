package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statut public de la plateforme (sans authentification).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformStatusDTO {
    private boolean maintenanceMode;
    private boolean allowNewRegistrations;
    private String maintenanceMessage;
}
