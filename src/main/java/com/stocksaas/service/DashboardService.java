package com.stocksaas.service;

import com.stocksaas.dto.DashboardStatsDTO;
import com.stocksaas.dto.SuperAdminDashboardStatsDTO;
import com.stocksaas.model.Company;
import com.stocksaas.model.CompanySubscriptionRecord;
import com.stocksaas.model.Invoice;
import com.stocksaas.model.Movement;
import com.stocksaas.model.Product;
import com.stocksaas.model.User;
import com.stocksaas.model.Warehouse;
import com.stocksaas.subscription.SubscriptionRequestStatusCode;
import com.stocksaas.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    private final InvoiceRepository invoiceRepository;
    
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
            totalWarehouses = warehouseRepository.countByCompanyIdAndNotDeleted(companyId);
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

        // Synthèse factures (requêtes ciblées — pas de chargement de toutes les factures)
        BigDecimal paidRevenue = BigDecimal.ZERO;
        BigDecimal pendingRevenue = BigDecimal.ZERO;
        long paidInvoicesCount = 0L;
        long draftInvoicesCount = 0L;
        long sentInvoicesCount = 0L;
        long cancelledInvoicesCount = 0L;
        for (Object[] row : invoiceRepository.summarizeByStatus(companyId)) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal sum = row.length > 2 && row[2] != null
                    ? (row[2] instanceof BigDecimal bd ? bd : new BigDecimal(row[2].toString()))
                    : BigDecimal.ZERO;
            switch (status) {
                case "PAID" -> {
                    paidInvoicesCount = count;
                    paidRevenue = sum;
                }
                case "DRAFT" -> draftInvoicesCount = count;
                case "SENT" -> {
                    sentInvoicesCount = count;
                    pendingRevenue = sum;
                }
                case "CANCELLED" -> cancelledInvoicesCount = count;
                default -> { }
            }
        }

        LocalDate salesStart = now.minusMonths(5).withDayOfMonth(1);
        List<DashboardStatsDTO.MonthlySalesData> salesByMonth =
                buildSalesByMonth(invoiceRepository.findPaidInvoicesSince(companyId, salesStart));

        Pageable invoicePage = PageRequest.of(0, 5);
        List<DashboardStatsDTO.InvoiceSummaryDTO> pendingInvoices = invoiceRepository
                .findPendingForDashboard(companyId, invoicePage)
                .stream()
                .map(this::mapToInvoiceSummaryDTO)
                .collect(Collectors.toList());
        List<DashboardStatsDTO.InvoiceSummaryDTO> recentInvoices = invoiceRepository
                .findRecentForDashboard(companyId, invoicePage)
                .stream()
                .map(this::mapToInvoiceSummaryDTO)
                .collect(Collectors.toList());

        List<DashboardStatsDTO.LowStockProductDTO> lowStockItems = stockLevelRepository
                .findLowStockProductsForDashboard(companyId)
                .stream()
                .map(this::mapToLowStockProductDTO)
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
                .paidRevenue(paidRevenue)
                .pendingRevenue(pendingRevenue)
                .paidInvoicesCount(paidInvoicesCount)
                .draftInvoicesCount(draftInvoicesCount)
                .sentInvoicesCount(sentInvoicesCount)
                .cancelledInvoicesCount(cancelledInvoicesCount)
                .salesByMonth(salesByMonth)
                .pendingInvoices(pendingInvoices)
                .recentInvoices(recentInvoices)
                .lowStockItems(lowStockItems)
                .productsChange("+12%")
                .warehousesChange("+2")
                .movementsChange("+8%")
                .alertsChange("-3")
                .build();
    }
    
    /**
     * Récupère les statistiques du dashboard Super Admin
     * Utilise NOT_SUPPORTED pour suspendre la transaction et éviter les problèmes de rollback
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SuperAdminDashboardStatsDTO getSuperAdminDashboardStats() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        LocalDateTime thisMonthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime lastMonthStart = previousMonth.atDay(1).atStartOfDay();

        Long activeCompanies = 0L;
        try {
            activeCompanies = countActiveCompanies();
        } catch (Exception e) {
            log.warn("Erreur lors du comptage des entreprises actives", e);
        }

        Long totalUsers = 0L;
        try {
            totalUsers = countTotalUsers();
        } catch (Exception e) {
            log.warn("Erreur lors du comptage des utilisateurs", e);
        }

        double revenueThisMonth = 0D;
        double revenueLastMonth = 0D;
        Long supportTickets = 0L;
        long newCompaniesThisMonth = 0L;
        long newUsersThisMonth = 0L;
        long newPendingThisMonth = 0L;
        long newPendingLastMonth = 0L;
        try {
            revenueThisMonth = safeAmount(subscriptionRecordRepository.sumApprovedAmountBetween(thisMonthStart, nextMonthStart));
            revenueLastMonth = safeAmount(subscriptionRecordRepository.sumApprovedAmountBetween(lastMonthStart, thisMonthStart));
            supportTickets = subscriptionRecordRepository.countByRequestStatusAndIsDeletedFalse(
                    SubscriptionRequestStatusCode.PENDING);
            newCompaniesThisMonth = companyRepository.countCreatedBetween(thisMonthStart, nextMonthStart);
            newUsersThisMonth = userRepository.countNonSuperAdminCreatedBetween(thisMonthStart, nextMonthStart);
            newPendingThisMonth = subscriptionRecordRepository.countByRequestStatusAndCreatedBetween(
                    SubscriptionRequestStatusCode.PENDING, thisMonthStart, nextMonthStart);
            newPendingLastMonth = subscriptionRecordRepository.countByRequestStatusAndCreatedBetween(
                    SubscriptionRequestStatusCode.PENDING, lastMonthStart, thisMonthStart);
        } catch (Exception e) {
            log.warn("Erreur lors du calcul des indicateurs Super Admin", e);
        }

        String monthlyRevenue = formatMoneyFcfa(revenueThisMonth);
        String companiesChange = formatMonthlyCountLabel(newCompaniesThisMonth, "entreprise", "entreprises");
        String usersChange = formatMonthlyCountLabel(newUsersThisMonth, "utilisateur", "utilisateurs");
        String revenueChange = formatPercentChange(revenueThisMonth, revenueLastMonth);
        String ticketsChange = formatPercentChange(newPendingThisMonth, newPendingLastMonth);

        java.time.LocalDateTime sixMonthsAgo = java.time.LocalDateTime.now().minusMonths(6);
        List<SuperAdminDashboardStatsDTO.MonthlyCompanyData> monthlyData = new ArrayList<>();
        try {
            monthlyData = getMonthlyCompaniesData(sixMonthsAgo);
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des données mensuelles", e);
        }
        
        List<SuperAdminDashboardStatsDTO.PlanDistributionData> planData = new ArrayList<>();
        try {
            planData = getPlanDistributionData();
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération de la distribution des plans", e);
        }

        List<SuperAdminDashboardStatsDTO.MonthlySubscriptionData> monthlySubscriptionsData = new ArrayList<>();
        try {
            monthlySubscriptionsData = getMonthlySubscriptionsData(sixMonthsAgo);
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des souscriptions mensuelles", e);
        }
        
        // Récupérer les entreprises récentes
        List<SuperAdminDashboardStatsDTO.RecentCompanyDTO> recentCompaniesDTO = new ArrayList<>();
        try {
            recentCompaniesDTO = getRecentCompaniesDTO();
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des entreprises récentes", e);
        }

        SuperAdminDashboardStatsDTO.ProductInsights productInsights = buildProductInsights(thisMonthStart, nextMonthStart);

        List<SuperAdminDashboardStatsDTO.CompanyProductRiskDTO> topRiskCompanies = new ArrayList<>();
        try {
            topRiskCompanies = getTopRiskCompaniesDTO();
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération du top entreprises à risque produit", e);
        }
        if (productInsights != null) {
            productInsights.setTopRiskCompanies(topRiskCompanies);
        }
        
        return SuperAdminDashboardStatsDTO.builder()
                .activeCompanies(activeCompanies)
                .totalUsers(totalUsers)
                .monthlyRevenue(monthlyRevenue)
                .supportTickets(supportTickets)
                .monthlyCompaniesData(monthlyData)
                .monthlySubscriptionsData(monthlySubscriptionsData)
                .planDistribution(planData)
                .recentCompanies(recentCompaniesDTO)
                .productInsights(productInsights)
                .companiesChange(companiesChange)
                .usersChange(usersChange)
                .revenueChange(revenueChange)
                .ticketsChange(ticketsChange)
                .build();
    }
    
    // Méthodes helper isolées dans leurs propres transactions
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    private Long countActiveCompanies() {
        return companyRepository.countActiveNotDeleted();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    private Long countTotalUsers() {
        return userRepository.countNonSuperAdminNotDeleted();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    private List<SuperAdminDashboardStatsDTO.RecentCompanyDTO> getRecentCompaniesDTO() {
        return companyRepository.findRecentNotDeletedWithPlanAndStatus(PageRequest.of(0, 5))
                .stream()
                .map(this::mapToRecentCompanyDTO)
                .collect(Collectors.toList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    private List<SuperAdminDashboardStatsDTO.CompanyProductRiskDTO> getTopRiskCompaniesDTO() {
        return companyRepository.findTopCompaniesByProductRisk()
                .stream()
                .map(this::mapToCompanyProductRiskDTO)
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }
    
    // Méthodes helper pour récupérer les données réelles depuis la base
    private List<DashboardStatsDTO.MonthlyMovementData> getMonthlyMovementsData(Long companyId, List<Long> warehouseIds, LocalDate startDate) {
        List<Object[]> results;
        if (warehouseIds != null && !warehouseIds.isEmpty()) {
            results = movementRepository.findMonthlyMovementsByCompanyAndWarehouses(companyId, warehouseIds, startDate);
        } else {
            results = movementRepository.findMonthlyMovementsByCompany(companyId, startDate);
        }

        String[] monthNames = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        YearMonth currentMonth = YearMonth.now();
        Map<String, DashboardStatsDTO.MonthlyMovementData> dataMap = new java.util.LinkedHashMap<>();

        for (int i = 5; i >= 0; i--) {
            YearMonth ym = currentMonth.minusMonths(i);
            String monthKey = ym.toString();
            String monthLabel = monthNames[ym.getMonthValue() - 1];
            dataMap.put(monthKey, DashboardStatsDTO.MonthlyMovementData.builder()
                    .month(monthLabel)
                    .entries(0L)
                    .exits(0L)
                    .transfers(0L)
                    .adjustments(0L)
                    .build());
        }

        for (Object[] result : results) {
            int year = ((Number) result[0]).intValue();
            int month = ((Number) result[1]).intValue();
            String typeCode = (String) result[2];
            long count = ((Number) result[3]).longValue();
            String monthKey = YearMonth.of(year, month).toString();

            DashboardStatsDTO.MonthlyMovementData data = dataMap.get(monthKey);
            if (data == null) {
                continue;
            }
            addMovementCount(data, typeCode, count);
        }

        return new ArrayList<>(dataMap.values());
    }

    private static void addMovementCount(DashboardStatsDTO.MonthlyMovementData data, String typeCode, long count) {
        String normalized = normalizeMovementTypeCode(typeCode);
        if (normalized == null) {
            return;
        }
        switch (normalized) {
            case "ENTREE" -> data.setEntries(safeAdd(data.getEntries(), count));
            case "SORTIE" -> data.setExits(safeAdd(data.getExits(), count));
            case "TRANSFERT" -> data.setTransfers(safeAdd(data.getTransfers(), count));
            case "AJUSTEMENT" -> data.setAdjustments(safeAdd(data.getAdjustments(), count));
            default -> { }
        }
    }

    private static long safeAdd(Long current, long count) {
        return (current != null ? current : 0L) + count;
    }

    private static String normalizeMovementTypeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String trimmed = code.trim();
        String upper = trimmed.toUpperCase();
        if ("ENTREE".equals(upper) || "ENTRÉE".equals(upper)) {
            return "ENTREE";
        }
        if ("SORTIE".equals(upper)) {
            return "SORTIE";
        }
        if ("TRANSFERT".equals(upper)) {
            return "TRANSFERT";
        }
        if ("AJUSTEMENT".equals(upper)) {
            return "AJUSTEMENT";
        }
        if ("Entrée".equalsIgnoreCase(trimmed)) {
            return "ENTREE";
        }
        if ("Sortie".equalsIgnoreCase(trimmed)) {
            return "SORTIE";
        }
        if ("Transfert".equalsIgnoreCase(trimmed)) {
            return "TRANSFERT";
        }
        if ("Ajustement".equalsIgnoreCase(trimmed)) {
            return "AJUSTEMENT";
        }
        return null;
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
    
    private List<SuperAdminDashboardStatsDTO.MonthlyCompanyData> getMonthlyCompaniesData(java.time.LocalDateTime startDate) {
        try {
            List<Object[]> results = companyRepository.countCompaniesByMonth(startDate);
            
            // Créer un map pour stocker les données par mois
            java.util.Map<String, Long> dataMap = new java.util.HashMap<>();
            String[] monthNames = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
            
            // Initialiser les 6 derniers mois
            java.time.LocalDateTime current = java.time.LocalDateTime.now();
            for (int i = 0; i < 6; i++) {
                java.time.LocalDateTime monthDate = current.minusMonths(i);
                String monthKey = monthNames[monthDate.getMonthValue() - 1];
                dataMap.put(monthKey, 0L);
            }
            
            // Remplir avec les données réelles
            if (results != null) {
                for (Object[] result : results) {
                    if (result != null && result.length >= 2) {
                        try {
                            String month = result[0] != null ? (String) result[0] : null;
                            Long count = result[1] != null ? ((Number) result[1]).longValue() : 0L;
                            if (month != null) {
                                dataMap.put(month, count);
                            }
                        } catch (Exception e) {
                            // Ignorer les erreurs de parsing
                        }
                    }
                }
            }
            
            // Retourner les 6 derniers mois dans l'ordre
            List<SuperAdminDashboardStatsDTO.MonthlyCompanyData> dataList = new ArrayList<>();
            for (int i = 5; i >= 0; i--) {
                java.time.LocalDateTime monthDate = current.minusMonths(i);
                String monthKey = monthNames[monthDate.getMonthValue() - 1];
                dataList.add(SuperAdminDashboardStatsDTO.MonthlyCompanyData.builder()
                        .month(monthKey)
                        .count(dataMap.getOrDefault(monthKey, 0L))
                        .build());
            }
            
            return dataList;
        } catch (Exception e) {
            // En cas d'erreur, retourner des données vides
            String[] monthNames = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun"};
            List<SuperAdminDashboardStatsDTO.MonthlyCompanyData> dataList = new ArrayList<>();
            for (String month : monthNames) {
                dataList.add(SuperAdminDashboardStatsDTO.MonthlyCompanyData.builder()
                        .month(month)
                        .count(0L)
                        .build());
            }
            return dataList;
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    private List<SuperAdminDashboardStatsDTO.MonthlySubscriptionData> getMonthlySubscriptionsData(LocalDateTime startDate) {
        String[] monthNames = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        LocalDateTime current = LocalDateTime.now();

        Map<YearMonth, long[]> countsByMonth = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            YearMonth ym = YearMonth.from(current.minusMonths(i));
            countsByMonth.put(ym, new long[3]);
        }

        List<CompanySubscriptionRecord> records =
                subscriptionRecordRepository.findByCreatedAtAfterAndIsDeletedFalse(startDate);
        for (CompanySubscriptionRecord record : records) {
            if (record.getCreatedAt() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(record.getCreatedAt());
            long[] counts = countsByMonth.get(ym);
            if (counts == null) {
                continue;
            }
            String status = record.getRequestStatus();
            if (SubscriptionRequestStatusCode.APPROVED.equals(status)) {
                counts[0]++;
            } else if (SubscriptionRequestStatusCode.REJECTED.equals(status)) {
                counts[1]++;
            } else if (SubscriptionRequestStatusCode.PENDING.equals(status)) {
                counts[2]++;
            }
        }

        List<SuperAdminDashboardStatsDTO.MonthlySubscriptionData> dataList = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.from(current.minusMonths(i));
            long[] counts = countsByMonth.getOrDefault(ym, new long[3]);
            String monthKey = monthNames[ym.getMonthValue() - 1];
            dataList.add(SuperAdminDashboardStatsDTO.MonthlySubscriptionData.builder()
                    .month(monthKey)
                    .approvedCount(counts[0])
                    .rejectedCount(counts[1])
                    .pendingCount(counts[2])
                    .build());
        }
        return dataList;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
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

    private SuperAdminDashboardStatsDTO.ProductInsights buildProductInsights(
            LocalDateTime thisMonthStart,
            LocalDateTime nextMonthStart
    ) {
        long totalProducts = 0L;
        long newProductsThisMonth = 0L;
        long outOfStockProducts = 0L;
        long lowStockProducts = 0L;
        long priceAnomalies = 0L;

        try {
            Long total = productRepository.countAllNotDeleted();
            Long created = productRepository.countCreatedBetween(thisMonthStart, nextMonthStart);
            totalProducts = total != null ? total : 0L;
            newProductsThisMonth = created != null ? created : 0L;
        } catch (Exception e) {
            log.warn("Erreur lors du comptage des produits plateforme", e);
        }

        try {
            outOfStockProducts = stockLevelRepository.countDistinctOutOfStockProductsPlatform();
            lowStockProducts = stockLevelRepository.countDistinctLowStockProductsPlatform();
        } catch (Exception e) {
            log.warn("Erreur lors du comptage des ruptures / stocks bas plateforme", e);
        }

        try {
            Long anomalies = productRepository.countPriceAnomalies();
            priceAnomalies = anomalies != null ? anomalies : 0L;
        } catch (Exception e) {
            log.warn("Erreur lors du comptage des anomalies prix", e);
        }

        return SuperAdminDashboardStatsDTO.ProductInsights.builder()
                .totalProducts(totalProducts)
                .newProductsThisMonth(newProductsThisMonth)
                .outOfStockProducts(outOfStockProducts)
                .lowStockProducts(lowStockProducts)
                .priceAnomalies(priceAnomalies)
                .topRiskCompanies(new ArrayList<>())
                .build();
    }

    private SuperAdminDashboardStatsDTO.CompanyProductRiskDTO mapToCompanyProductRiskDTO(Object[] row) {
        if (row == null || row.length < 5) {
            return null;
        }
        return SuperAdminDashboardStatsDTO.CompanyProductRiskDTO.builder()
                .companyId(toLong(row[0]))
                .companyName(row[1] != null ? row[1].toString() : "N/A")
                .outOfStockProducts(toLong(row[2]))
                .lowStockProducts(toLong(row[3]))
                .riskScore(toLong(row[4]))
                .build();
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
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

    private List<DashboardStatsDTO.MonthlySalesData> buildSalesByMonth(List<Invoice> paidInvoices) {
        Map<String, BigDecimal> monthlyMap = new HashMap<>();
        LocalDate current = LocalDate.now();
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MM/yy");

        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = current.minusMonths(i).withDayOfMonth(1);
            String monthKey = YearMonth.from(monthDate).toString();
            monthlyMap.put(monthKey, BigDecimal.ZERO);
        }

        for (Invoice invoice : paidInvoices) {
            if (invoice.getInvoiceDate() == null) {
                continue;
            }
            String monthKey = YearMonth.from(invoice.getInvoiceDate()).toString();
            if (monthlyMap.containsKey(monthKey)) {
                BigDecimal total = invoice.getTotal() != null ? invoice.getTotal() : BigDecimal.ZERO;
                monthlyMap.merge(monthKey, total, BigDecimal::add);
            }
        }

        List<DashboardStatsDTO.MonthlySalesData> dataList = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = current.minusMonths(i).withDayOfMonth(1);
            String monthKey = YearMonth.from(monthDate).toString();
            dataList.add(DashboardStatsDTO.MonthlySalesData.builder()
                    .monthKey(monthKey)
                    .label(monthDate.format(labelFormatter))
                    .amount(monthlyMap.getOrDefault(monthKey, BigDecimal.ZERO))
                    .build());
        }
        return dataList;
    }

    private DashboardStatsDTO.InvoiceSummaryDTO mapToInvoiceSummaryDTO(Invoice invoice) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return DashboardStatsDTO.InvoiceSummaryDTO.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .clientName(invoice.getClient() != null ? invoice.getClient().getName() : null)
                .total(invoice.getTotal())
                .status(invoice.getStatus())
                .statusLabel(invoiceStatusToLabel(invoice.getStatus()))
                .invoiceDate(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(formatter) : null)
                .build();
    }

    private DashboardStatsDTO.LowStockProductDTO mapToLowStockProductDTO(Object[] row) {
        return DashboardStatsDTO.LowStockProductDTO.builder()
                .id(row[0] != null ? ((Number) row[0]).longValue() : null)
                .name(row[1] != null ? row[1].toString() : null)
                .stock(row[2] != null
                        ? (row[2] instanceof BigDecimal bd ? bd : new BigDecimal(row[2].toString()))
                        : BigDecimal.ZERO)
                .minThreshold(row[3] != null
                        ? (row[3] instanceof BigDecimal bd ? bd : new BigDecimal(row[3].toString()))
                        : BigDecimal.ZERO)
                .build();
    }

    private static String invoiceStatusToLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "SENT" -> "Envoyée";
            case "PAID" -> "Payée";
            case "CANCELLED" -> "Annulée";
            default -> "Brouillon";
        };
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

    private static double safeAmount(Double value) {
        return value != null ? value : 0D;
    }

    private static String formatMoneyFcfa(double amount) {
        return String.format("%,.0f", amount).replace('\u00a0', ' ').replace(',', ' ') + " FCFA";
    }

    private static String formatMonthlyCountLabel(long count, String singular, String plural) {
        if (count <= 0) {
            return "Aucun nouveau ce mois";
        }
        if (count == 1) {
            return "+1 " + singular + " ce mois";
        }
        return "+" + count + " " + plural + " ce mois";
    }

    private static String formatPercentChange(long current, long previous) {
        return formatPercentChange((double) current, (double) previous);
    }

    private static String formatPercentChange(double current, double previous) {
        if (previous <= 0D) {
            if (current <= 0D) {
                return "Stable";
            }
            return "+100% vs mois dernier";
        }
        double pct = ((current - previous) * 100D) / previous;
        if (Math.abs(pct) < 0.5D) {
            return "Stable";
        }
        if (pct > 0D) {
            return String.format("+%.0f%% vs mois dernier", pct);
        }
        return String.format("%.0f%% vs mois dernier", pct);
    }
}
