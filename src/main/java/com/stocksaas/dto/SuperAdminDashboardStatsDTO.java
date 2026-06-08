package com.stocksaas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour les statistiques du dashboard Super Admin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuperAdminDashboardStatsDTO {
    private Long activeCompanies;
    private Long totalUsers;
    private String monthlyRevenue;
    private Long supportTickets;
    
    // Données pour les graphiques
    private List<MonthlyCompanyData> monthlyCompaniesData;
    private List<PlanDistributionData> planDistribution;
    
    // Entreprises récentes
    private List<RecentCompanyDTO> recentCompanies;
    
    // Variations
    private String companiesChange;
    private String usersChange;
    private String revenueChange;
    private String ticketsChange;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyCompanyData {
        private String month;
        private Long count;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanDistributionData {
        private String plan;
        private Long count;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentCompanyDTO {
        private Long id;
        private String name;
        private String email;
        private String plan;
        private String createdAt;
        private String status;
    }
}
