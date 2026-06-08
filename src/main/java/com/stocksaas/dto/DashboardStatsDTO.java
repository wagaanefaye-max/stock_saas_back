package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO pour les statistiques du dashboard
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDTO {
    // Statistiques principales
    private Long totalProducts;
    private Long totalWarehouses;
    private Long monthlyMovements;
    private Long alerts;
    private Long activeUsers; // Pour le dashboard Company Admin
    
    // Données pour les graphiques
    private List<MonthlyMovementData> monthlyMovementsData; // Pour le graphique linéaire
    private List<CategoryData> productsByCategory; // Pour le graphique doughnut
    
    // Mouvements récents
    private List<RecentMovementDTO> recentMovements;
    
    // Variations (pourcentage ou nombre)
    private String productsChange;
    private String warehousesChange;
    private String movementsChange;
    private String alertsChange;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyMovementData {
        private String month;
        private Long entries;
        private Long exits;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryData {
        private String category;
        private Long count;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentMovementDTO {
        private Long id;
        private String date;
        private String productName;
        private String movementType;
        private Long quantity;
        private String warehouseName;
    }
}
