package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    // Synthèse factures (évite de charger toutes les factures côté client)
    private BigDecimal paidRevenue;
    private BigDecimal pendingRevenue;
    private Long paidInvoicesCount;
    private Long draftInvoicesCount;
    private Long sentInvoicesCount;
    private Long cancelledInvoicesCount;
    private List<MonthlySalesData> salesByMonth;
    private List<InvoiceSummaryDTO> pendingInvoices;
    private List<InvoiceSummaryDTO> recentInvoices;
    private List<LowStockProductDTO> lowStockItems;
    
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
        private Long transfers;
        private Long adjustments;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlySalesData {
        private String monthKey;
        private String label;
        private BigDecimal amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceSummaryDTO {
        private Long id;
        private String invoiceNumber;
        private String clientName;
        private BigDecimal total;
        private String status;
        private String statusLabel;
        private String invoiceDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LowStockProductDTO {
        private Long id;
        private String name;
        private BigDecimal stock;
        private BigDecimal minThreshold;
    }
}
