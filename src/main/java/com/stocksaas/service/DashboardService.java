package com.stocksaas.service;

import com.stocksaas.dto.DashboardStatsDTO;
import com.stocksaas.dto.SuperAdminDashboardStatsDTO;
import com.stocksaas.model.Company;
import com.stocksaas.model.Movement;
import com.stocksaas.model.Product;
import com.stocksaas.model.User;
import com.stocksaas.model.Warehouse;
import com.stocksaas.subscription.SubscriptionRequestStatusCode;
import com.stocksaas.repository.*;
import com.stocksaas.util.DashboardMonthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service pour les statistiques du dashboard
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final MovementRepository movementRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final UserWarehouseRepository userWarehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final CompanySubscriptionRecordRepository subscriptionRecordRepository;
    
    /**
     * Récupère les statistiques du dashboard pour un utilisateur
     */
    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats(Long userId, Long companyId, List<Long> warehouseIds) {
        // Calculer les dates pour le mois en cours
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());
        
        // Compter les produits
        // Les produits appartiennent à l'entreprise, pas à un entrepôt spécifique
        Long totalProducts = productRepository.countByCompanyIdAndNotDeleted(companyId);
        
        // Compter les entrepôts
        Long totalWarehouses;
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            totalWarehouses = (long) warehouseIds.size();
        } else {
            // Compter tous les entrepôts de l'entreprise non supprimés
            totalWarehouses = warehouseRepository.findAll().stream()
                    .filter(w -> w.getCompany() != null && 
                               w.getCompany().getId().equals(companyId) && 
                               !w.getIsDeleted())
                    .count();
        }
        
        // Compter les mouvements du mois
        Long monthlyMovements;
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            monthlyMovements = movementRepository.countByCompanyIdAndWarehouseIdsAndDateRange(
                    companyId, warehouseIds, startOfMonth, endOfMonth);
        } else {
            monthlyMovements = movementRepository.countByCompanyIdAndDateRange(
                    companyId, startOfMonth, endOfMonth);
        }
        
        boolean restrictWarehouses = warehouseIds != null && !warehouseIds.isEmpty();
        long alerts = stockLevelRepository.countLowStockByCompany(
                companyId,
                restrictWarehouses,
                restrictWarehouses ? warehouseIds : List.of(0L));
        
        // Compter les utilisateurs actifs de l'entreprise
        Long activeUsers = userRepository.countActiveUsersByCompanyId(companyId);
        
        // Récupérer les données réelles pour les graphiques
        LocalDate sixMonthsAgo = now.minusMonths(6);
        List<DashboardStatsDTO.MonthlyMovementData> monthlyData;
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            monthlyData = getMonthlyMovementsData(companyId, warehouseIds, sixMonthsAgo);
        } else {
            monthlyData = getMonthlyMovementsData(companyId, null, sixMonthsAgo);
        }
        
        // Récupérer les produits par catégorie depuis la base
        List<DashboardStatsDTO.CategoryData> categoryData;
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            categoryData = getProductsByCategory(companyId, warehouseIds);
        } else {
            categoryData = getProductsByCategory(companyId, null);
        }
        
        // Récupérer les mouvements récents
        Pageable pageable = PageRequest.of(0, 5);
        List<Movement> recentMovements = movementRepository.findRecentByCompanyId(companyId, pageable);
        List<DashboardStatsDTO.RecentMovementDTO> recentMovementsDTO = recentMovements.stream()
                .map(this::mapToRecentMovementDTO)
                .collect(Collectors.toList());
        
        return DashboardStatsDTO.builder()
                .totalProducts(totalProducts != null ? totalProducts : 0L)
                .totalWarehouses(totalWarehouses != null ? totalWarehouses : 0L)
                .monthlyMovements(monthlyMovements != null ? monthlyMovements : 0L)
                .alerts(alerts)
                .activeUsers(activeUsers != null ? activeUsers : 0L)
                .monthlyMovementsData(monthlyData)
                .productsByCategory(categoryData)
                .recentMovements(recentMovementsDTO)
                .productsChange("+12%")
                .warehousesChange("+2")
                .movementsChange("+8%")
                .alertsChange("-3")
                .build();
    }
    
    /**
     * Statistiques du dashboard Super Admin (requêtes SQL ciblées, cache 2 min).
     */
    @Cacheable(cacheNames = "superAdminDashboardStats")
    @Transactional(readOnly = true)
    public SuperAdminDashboardStatsDTO getSuperAdminDashboardStats() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        LocalDateTime currentStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime currentEnd = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime prevStart = previousMonth.atDay(1).atStartOfDay();
        LocalDateTime prevEnd = previousMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);

        long activeCompanies = companyRepository.countActiveCompanies();
        long totalUsers = userRepository.countAllExceptSuperAdmin();
        long supportTickets = subscriptionRecordRepository.countByRequestStatusAndIsDeletedFalse(
                SubscriptionRequestStatusCode.PENDING);

        double currentRevenue = 0D;
        double previousRevenue = 0D;
        Object[] revenueRow = subscriptionRecordRepository.sumApprovedRevenueCurrentAndPrevious(
                currentStart, currentEnd, prevStart, prevEnd);
        if (revenueRow != null && revenueRow.length >= 2) {
            currentRevenue = toDouble(revenueRow[0]);
            previousRevenue = toDouble(revenueRow[1]);
        }

        long companiesCurrentMonth = 0L;
        long companiesPreviousMonth = 0L;
        Object[] companiesRow = companyRepository.countCompaniesCreatedCurrentAndPrevious(
                currentStart, currentEnd, prevStart, prevEnd);
        if (companiesRow != null && companiesRow.length >= 2) {
            companiesCurrentMonth = toLong(companiesRow[0]);
            companiesPreviousMonth = toLong(companiesRow[1]);
        }

        long usersCurrentMonth = 0L;
        long usersPreviousMonth = 0L;
        Object[] usersRow = userRepository.countUsersCreatedCurrentAndPrevious(
                currentStart, currentEnd, prevStart, prevEnd);
        if (usersRow != null && usersRow.length >= 2) {
            usersCurrentMonth = toLong(usersRow[0]);
            usersPreviousMonth = toLong(usersRow[1]);
        }

        long pendingCurrentMonth = 0L;
        long pendingPreviousMonth = 0L;
        Object[] pendingRow = subscriptionRecordRepository.countPendingSubscriptionsCurrentAndPrevious(
                currentStart, currentEnd, prevStart, prevEnd);
        if (pendingRow != null && pendingRow.length >= 2) {
            pendingCurrentMonth = toLong(pendingRow[0]);
            pendingPreviousMonth = toLong(pendingRow[1]);
        }

        NumberFormat revenueFormatter = NumberFormat.getIntegerInstance(Locale.FRENCH);
        String monthlyRevenue = revenueFormatter.format(Math.round(currentRevenue)) + " FCFA";

        List<Company> recentCompanies = companyRepository.findRecentWithPlanAndStatus(PageRequest.of(0, 5));
        List<SuperAdminDashboardStatsDTO.RecentCompanyDTO> recentCompaniesDTO = recentCompanies.stream()
                .map(this::mapToRecentCompanyDTO)
                .collect(Collectors.toList());

        return SuperAdminDashboardStatsDTO.builder()
                .activeCompanies(activeCompanies)
                .totalUsers(totalUsers)
                .monthlyRevenue(monthlyRevenue)
                .supportTickets(supportTickets)
                .monthlyCompaniesData(getMonthlyCompaniesData(sixMonthsAgo))
                .monthlySubscriptionsData(getMonthlySubscriptionsData(sixMonthsAgo))
                .planDistribution(getPlanDistributionData())
                .recentCompanies(recentCompaniesDTO)
                .companiesChange(formatDelta(companiesCurrentMonth, companiesPreviousMonth))
                .usersChange(formatPercentDelta(usersCurrentMonth, usersPreviousMonth))
                .revenueChange(formatPercentDelta(Math.round(currentRevenue), Math.round(previousRevenue)))
                .ticketsChange(formatDelta(pendingCurrentMonth, pendingPreviousMonth))
                .build();
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private String formatDelta(long current, long previous) {
        long delta = current - previous;
        if (delta > 0) {
            return "+" + delta;
        }
        return String.valueOf(delta);
    }

    private String formatPercentDelta(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? "+100%" : "0%";
        }
        long deltaPercent = Math.round(((current - previous) * 100.0) / previous);
        if (deltaPercent > 0) {
            return "+" + deltaPercent + "%";
        }
        return deltaPercent + "%";
    }
    
    // Méthodes helper pour récupérer les données réelles depuis la base
    private List<DashboardStatsDTO.MonthlyMovementData> getMonthlyMovementsData(Long companyId, List<Long> warehouseIds, LocalDate startDate) {
        List<Object[]> results;
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            results = movementRepository.findMonthlyMovementsByCompanyAndWarehouses(companyId, warehouseIds, startDate);
        } else {
            results = movementRepository.findMonthlyMovementsByCompany(companyId, startDate);
        }
        
        // Créer un map pour stocker les données par mois
        java.util.Map<String, DashboardStatsDTO.MonthlyMovementData> dataMap = new java.util.HashMap<>();
        
        // Initialiser les 6 derniers mois
        String[] monthNames = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        LocalDate current = LocalDate.now();
        for (int i = 0; i < 6; i++) {
            LocalDate monthDate = current.minusMonths(i);
            String monthKey = monthNames[monthDate.getMonthValue() - 1];
            dataMap.put(monthKey, DashboardStatsDTO.MonthlyMovementData.builder()
                    .month(monthKey)
                    .entries(0L)
                    .exits(0L)
                    .build());
        }
        
        // Remplir avec les données réelles
        for (Object[] result : results) {
            String month = (String) result[0];
            String typeCode = (String) result[1];
            Long count = ((Number) result[2]).longValue();
            
            DashboardStatsDTO.MonthlyMovementData data = dataMap.getOrDefault(month, 
                    DashboardStatsDTO.MonthlyMovementData.builder()
                            .month(month)
                            .entries(0L)
                            .exits(0L)
                            .build());
            
            if ("ENTREE".equals(typeCode)) {
                data.setEntries(count);
            } else if ("SORTIE".equals(typeCode)) {
                data.setExits(count);
            }
            
            dataMap.put(month, data);
        }
        
        // Retourner les 6 derniers mois dans l'ordre
        List<DashboardStatsDTO.MonthlyMovementData> dataList = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = current.minusMonths(i);
            String monthKey = monthNames[monthDate.getMonthValue() - 1];
            dataList.add(dataMap.getOrDefault(monthKey, DashboardStatsDTO.MonthlyMovementData.builder()
                    .month(monthKey)
                    .entries(0L)
                    .exits(0L)
                    .build()));
        }
        
        return dataList;
    }
    
    private List<DashboardStatsDTO.CategoryData> getProductsByCategory(Long companyId, List<Long> warehouseIds) {
        // Les produits appartiennent à l'entreprise, pas à un entrepôt spécifique
        List<Object[]> results = productRepository.countProductsByCategory(companyId);
        
        List<DashboardStatsDTO.CategoryData> data = new ArrayList<>();
        for (Object[] result : results) {
            com.stocksaas.model.ProductCategory productCategory = (com.stocksaas.model.ProductCategory) result[0];
            Long count = ((Number) result[1]).longValue();
            String categoryLabel = productCategory != null && productCategory.getLabel() != null
                    ? productCategory.getLabel() : "Autres";
            data.add(DashboardStatsDTO.CategoryData.builder()
                    .category(categoryLabel)
                    .count(count)
                    .build());
        }
        
        // Si aucune catégorie, retourner une liste vide
        return data.isEmpty() ? new ArrayList<>() : data;
    }
    
    private List<SuperAdminDashboardStatsDTO.MonthlyCompanyData> getMonthlyCompaniesData(LocalDateTime startDate) {
        Map<YearMonth, Long> countsByMonth = new HashMap<>();
        for (YearMonth ym : DashboardMonthUtils.lastSixMonthsChronological()) {
            countsByMonth.put(ym, 0L);
        }

        List<Object[]> results = companyRepository.countCompaniesByYearMonth(startDate);
        if (results != null) {
            for (Object[] row : results) {
                if (row == null || row.length < 3) {
                    continue;
                }
                YearMonth ym = YearMonth.of((int) toLong(row[0]), (int) toLong(row[1]));
                countsByMonth.put(ym, toLong(row[2]));
            }
        }

        List<SuperAdminDashboardStatsDTO.MonthlyCompanyData> dataList = new ArrayList<>(6);
        for (YearMonth ym : DashboardMonthUtils.lastSixMonthsChronological()) {
            dataList.add(SuperAdminDashboardStatsDTO.MonthlyCompanyData.builder()
                    .month(DashboardMonthUtils.label(ym))
                    .count(countsByMonth.getOrDefault(ym, 0L))
                    .build());
        }
        return dataList;
    }

    private List<SuperAdminDashboardStatsDTO.MonthlySubscriptionData> getMonthlySubscriptionsData(LocalDateTime startDate) {
        Map<YearMonth, long[]> countsByMonth = new HashMap<>();
        for (YearMonth ym : DashboardMonthUtils.lastSixMonthsChronological()) {
            countsByMonth.put(ym, new long[3]);
        }

        List<Object[]> results = subscriptionRecordRepository.countSubscriptionsByYearMonthAndStatus(startDate);
        if (results != null) {
            for (Object[] row : results) {
                if (row == null || row.length < 4) {
                    continue;
                }
                YearMonth ym = YearMonth.of((int) toLong(row[0]), (int) toLong(row[1]));
                long[] counts = countsByMonth.get(ym);
                if (counts == null) {
                    continue;
                }
                String status = row[2] != null ? row[2].toString() : "";
                long count = toLong(row[3]);
                if (SubscriptionRequestStatusCode.APPROVED.equals(status)) {
                    counts[0] = count;
                } else if (SubscriptionRequestStatusCode.REJECTED.equals(status)) {
                    counts[1] = count;
                } else if (SubscriptionRequestStatusCode.PENDING.equals(status)) {
                    counts[2] = count;
                }
            }
        }

        List<SuperAdminDashboardStatsDTO.MonthlySubscriptionData> dataList = new ArrayList<>(6);
        for (YearMonth ym : DashboardMonthUtils.lastSixMonthsChronological()) {
            long[] counts = countsByMonth.getOrDefault(ym, new long[3]);
            dataList.add(SuperAdminDashboardStatsDTO.MonthlySubscriptionData.builder()
                    .month(DashboardMonthUtils.label(ym))
                    .approvedCount(counts[0])
                    .rejectedCount(counts[1])
                    .pendingCount(counts[2])
                    .build());
        }
        return dataList;
    }

    private List<SuperAdminDashboardStatsDTO.PlanDistributionData> getPlanDistributionData() {
        try {
            List<Object[]> results = companyRepository.countCompaniesByPlan();
            
            List<SuperAdminDashboardStatsDTO.PlanDistributionData> data = new ArrayList<>();
            if (results != null) {
                for (Object[] result : results) {
                    if (result != null && result.length >= 2) {
                        try {
                            String planCode = result[0] != null ? (String) result[0] : "N/A";
                            Long count = result[1] != null ? ((Number) result[1]).longValue() : 0L;
                            
                            // Mapper les codes de plan aux labels
                            String planLabel = mapPlanCodeToLabel(planCode);
                            
                            data.add(SuperAdminDashboardStatsDTO.PlanDistributionData.builder()
                                    .plan(planLabel)
                                    .count(count)
                                    .build());
                        } catch (Exception e) {
                            log.warn("Erreur lors du parsing d'un résultat de plan", e);
                        }
                    }
                }
            }
            
            return data.isEmpty() ? new ArrayList<>() : data;
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération de la distribution des plans", e);
            // En cas d'erreur, retourner une liste vide
            return new ArrayList<>();
        }
    }
    
    /**
     * Mappe un code de plan à son label
     */
    private String mapPlanCodeToLabel(String planCode) {
        if (planCode == null || planCode.equals("N/A")) {
            return "N/A";
        }
        // Mapping simple des codes aux labels
        switch (planCode.toUpperCase()) {
            case "FREE":
            case "GRATUIT":
                return "Gratuit";
            case "BASIC":
            case "BASIQUE":
                return "Basique";
            case "STANDARD":
                return "Standard";
            case "PREMIUM":
                return "Premium";
            default:
                return planCode; // Retourner le code si non reconnu
        }
    }
    
    private DashboardStatsDTO.RecentMovementDTO mapToRecentMovementDTO(Movement movement) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return DashboardStatsDTO.RecentMovementDTO.builder()
                .id(movement.getId())
                .date(movement.getDate().format(formatter))
                .productName(movement.getProduct() != null ? movement.getProduct().getName() : "N/A")
                .movementType(movement.getType() != null ? movement.getType().getLabel() : "N/A")
                .quantity(movement.getQuantity() != null ? movement.getQuantity().longValue() : 0L)
                .warehouseName(movement.getWarehouse() != null ? movement.getWarehouse().getName() : "N/A")
                .build();
    }
    
    private SuperAdminDashboardStatsDTO.RecentCompanyDTO mapToRecentCompanyDTO(Company company) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String createdAtStr = "N/A";
        if (company.getCreatedAt() != null) {
            try {
                createdAtStr = company.getCreatedAt().format(formatter);
            } catch (Exception e) {
                createdAtStr = "N/A";
            }
        }
        return SuperAdminDashboardStatsDTO.RecentCompanyDTO.builder()
                .id(company.getId())
                .name(company.getName() != null ? company.getName() : "N/A")
                .email(company.getEmail() != null ? company.getEmail() : "N/A")
                .plan(company.getPlan() != null ? company.getPlan().getLabel() : "N/A")
                .createdAt(createdAtStr)
                .status(company.getStatus() != null ? company.getStatus().getLabel() : "N/A")
                .build();
    }
}
