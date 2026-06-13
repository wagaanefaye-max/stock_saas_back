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
        // Compter les entreprises actives
        Long activeCompanies = 0L;
        try {
            activeCompanies = countActiveCompanies();
        } catch (Exception e) {
            log.warn("Erreur lors du comptage des entreprises actives", e);
        }
        
        // Compter les utilisateurs totaux (sauf SUPER_ADMIN)
        Long totalUsers = 0L;
        try {
            totalUsers = countTotalUsers();
        } catch (Exception e) {
            log.warn("Erreur lors du comptage des utilisateurs", e);
        }
        
        // Pour l'instant, revenus et tickets sont des valeurs de base
        String monthlyRevenue = "0 FCFA";
        Long supportTickets = 0L;
        
        // Récupérer les données réelles pour les graphiques
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
        List<Company> recentCompanies = new ArrayList<>();
        try {
            recentCompanies = getRecentCompanies();
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des entreprises récentes", e);
        }
        
        List<SuperAdminDashboardStatsDTO.RecentCompanyDTO> recentCompaniesDTO = new ArrayList<>();
        try {
            recentCompaniesDTO = recentCompanies.stream()
                    .map(this::mapToRecentCompanyDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Erreur lors du mapping des entreprises récentes", e);
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
                .companiesChange("+0")
                .usersChange("+0%")
                .revenueChange("+0%")
                .ticketsChange("0")
                .build();
    }
    
    // Méthodes helper isolées dans leurs propres transactions
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    private Long countActiveCompanies() {
        return companyRepository.findAll().stream()
                .filter(c -> c != null && !c.getIsDeleted() && 
                           c.getStatus() != null && 
                           "ACTIF".equals(c.getStatus().getCode()))
                .count();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    private Long countTotalUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u != null && !u.getIsDeleted() && !u.isSuperAdmin())
                .count();
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    private List<Company> getRecentCompanies() {
        return companyRepository.findAll().stream()
                .filter(c -> c != null && !c.getIsDeleted() && c.getCreatedAt() != null)
                .sorted((c1, c2) -> {
                    if (c1.getCreatedAt() == null && c2.getCreatedAt() == null) return 0;
                    if (c1.getCreatedAt() == null) return 1;
                    if (c2.getCreatedAt() == null) return -1;
                    return c2.getCreatedAt().compareTo(c1.getCreatedAt());
                })
                .limit(5)
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
}
