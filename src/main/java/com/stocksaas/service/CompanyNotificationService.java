package com.stocksaas.service;

import com.stocksaas.model.Company;
import com.stocksaas.model.Movement;
import com.stocksaas.model.User;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.InvoiceRepository;
import com.stocksaas.repository.MovementRepository;
import com.stocksaas.repository.ProductRepository;
import com.stocksaas.repository.StockLevelRepository;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyNotificationService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final StockLevelRepository stockLevelRepository;
    private final MovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:4200}")
    private String appBaseUrl;

    /**
     * Notification immédiate après une entrée ou sortie de stock.
     */
    @Transactional(readOnly = true)
    public void sendMovementNotification(Long movementId) {
        if (movementId == null) {
            return;
        }
        Movement movement = movementRepository.findByIdWithDetails(movementId).orElse(null);
        if (movement == null || Boolean.TRUE.equals(movement.getIsDeleted())) {
            return;
        }

        Company company = movement.getCompany();
        if (company == null || Boolean.TRUE.equals(company.getIsDeleted()) || !isMovementsEnabled(company)) {
            return;
        }

        String typeCode = normalizeMovementType(movement.getType().getCode());
        if (!"ENTREE".equals(typeCode) && !"SORTIE".equals(typeCode)) {
            return;
        }

        Set<String> recipients = collectRecipientEmails(company);
        if (recipients.isEmpty()) {
            log.warn("Aucun destinataire pour notification mouvement entreprise {}", company.getId());
            return;
        }

        String typeLabel = "ENTREE".equals(typeCode) ? "Entrée" : "Sortie";
        String productName = movement.getProduct().getName();
        String warehouseName = movement.getWarehouse().getName();
        String quantity = formatQuantity(movement.getQuantity());
        String dateLabel = movement.getDate() != null ? movement.getDate().format(DATE_FMT) : "—";
        String performedBy = movement.getUser() != null ? movement.getUser().getName() : "Utilisateur";
        String dashboardUrl = buildUrl("/company-admin/movements");
        List<User> admins = userRepository.findCompanyAdminsByCompanyId(company.getId());

        for (String email : recipients) {
            String recipientName = resolveRecipientName(admins, company.getName(), email);
            emailService.sendStockMovementNotification(
                    email,
                    recipientName,
                    company.getName(),
                    typeLabel,
                    productName,
                    warehouseName,
                    quantity,
                    dateLabel,
                    performedBy,
                    dashboardUrl
            );
        }
    }

    /**
     * Digest quotidien des produits en stock bas.
     */
    @Transactional(readOnly = true)
    public int sendDailyLowStockDigests() {
        List<Company> companies = companyRepository.findActiveWithLowStockNotificationsEnabled();
        int sent = 0;
        for (Company company : companies) {
            try {
                sent += sendLowStockDigestForCompany(company);
            } catch (Exception e) {
                log.warn("Digest stock bas ignoré pour entreprise {}: {}", company.getId(), e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Digests stock bas envoyés: {} e-mail(s)", sent);
        }
        return sent;
    }

    /**
     * Résumé hebdomadaire par e-mail.
     */
    @Transactional(readOnly = true)
    public int sendWeeklyReports() {
        List<Company> companies = companyRepository.findActiveWithWeeklyReportEnabled();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6);
        int sent = 0;

        for (Company company : companies) {
            try {
                sent += sendWeeklyReportForCompany(company, startDate, endDate);
            } catch (Exception e) {
                log.warn("Résumé hebdo ignoré pour entreprise {}: {}", company.getId(), e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Résumés hebdomadaires envoyés: {} e-mail(s)", sent);
        }
        return sent;
    }

    private int sendLowStockDigestForCompany(Company company) {
        List<Object[]> rows = stockLevelRepository.findLowStockDetailsByCompany(company.getId());
        if (rows.isEmpty()) {
            return 0;
        }

        Set<String> recipients = collectRecipientEmails(company);
        if (recipients.isEmpty()) {
            return 0;
        }

        List<String> lines = new ArrayList<>();
        for (Object[] row : rows) {
            String productName = (String) row[0];
            String warehouseName = (String) row[1];
            BigDecimal quantity = row[2] instanceof BigDecimal bd ? bd : new BigDecimal(row[2].toString());
            BigDecimal threshold = row[3] instanceof BigDecimal bd ? bd : new BigDecimal(row[3].toString());
            lines.add(String.format("- %s (%s) : stock %s / seuil %s",
                    productName, warehouseName, formatQuantity(quantity), formatQuantity(threshold)));
        }

        long totalAlerts = stockLevelRepository.countDistinctLowStockProductsByCompany(company.getId());
        String productsUrl = buildUrl("/company-admin/products");
        List<User> admins = userRepository.findCompanyAdminsByCompanyId(company.getId());

        for (String email : recipients) {
            String recipientName = resolveRecipientName(admins, company.getName(), email);
            emailService.sendLowStockAlert(
                    email,
                    recipientName,
                    company.getName(),
                    totalAlerts,
                    lines,
                    productsUrl
            );
        }
        return recipients.size();
    }

    private int sendWeeklyReportForCompany(Company company, LocalDate startDate, LocalDate endDate) {
        Set<String> recipients = collectRecipientEmails(company);
        if (recipients.isEmpty()) {
            return 0;
        }

        long productCount = productRepository.countByCompanyIdAndNotDeleted(company.getId());
        long warehouseCount = warehouseRepository.countByCompanyIdAndNotDeleted(company.getId());
        long lowStockCount = stockLevelRepository.countDistinctLowStockProductsByCompany(company.getId());
        Long movementCount = movementRepository.countByCompanyIdAndDateRange(company.getId(), startDate, endDate);
        long invoiceCount = invoiceRepository.countByCompanyIdAndInvoiceDateRange(company.getId(), startDate, endDate);

        MovementSummary movementSummary = summarizeMovements(
                movementRepository.countMovementsByTypeInRange(company.getId(), startDate, endDate));

        String periodLabel = startDate.format(DATE_FMT) + " — " + endDate.format(DATE_FMT);
        String dashboardUrl = buildUrl("/company-admin/dashboard");
        List<User> admins = userRepository.findCompanyAdminsByCompanyId(company.getId());

        for (String email : recipients) {
            String recipientName = resolveRecipientName(admins, company.getName(), email);
            emailService.sendWeeklyStockReport(
                    email,
                    recipientName,
                    company.getName(),
                    periodLabel,
                    productCount,
                    warehouseCount,
                    movementCount != null ? movementCount : 0L,
                    movementSummary.entries(),
                    movementSummary.exits(),
                    movementSummary.transfers(),
                    movementSummary.adjustments(),
                    lowStockCount,
                    invoiceCount,
                    dashboardUrl
            );
        }
        return recipients.size();
    }

    private MovementSummary summarizeMovements(List<Object[]> rows) {
        long entries = 0;
        long exits = 0;
        long transfers = 0;
        long adjustments = 0;
        for (Object[] row : rows) {
            String typeCode = row[0] != null ? row[0].toString() : "";
            long count = ((Number) row[1]).longValue();
            String normalized = normalizeMovementType(typeCode);
            switch (normalized) {
                case "ENTREE" -> entries += count;
                case "SORTIE" -> exits += count;
                case "TRANSFERT" -> transfers += count;
                case "AJUSTEMENT" -> adjustments += count;
                default -> { }
            }
        }
        return new MovementSummary(entries, exits, transfers, adjustments);
    }

    private Set<String> collectRecipientEmails(Company company) {
        Set<String> emails = new LinkedHashSet<>();
        if (company.getEmail() != null && !company.getEmail().isBlank()) {
            emails.add(company.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        for (User admin : userRepository.findCompanyAdminsByCompanyId(company.getId())) {
            if (admin.getEmail() != null && !admin.getEmail().isBlank()) {
                emails.add(admin.getEmail().trim().toLowerCase(Locale.ROOT));
            }
        }
        return emails;
    }

    private String resolveRecipientName(List<User> admins, String companyName, String email) {
        return admins.stream()
                .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(email))
                .map(User::getName)
                .findFirst()
                .orElse(companyName);
    }

    private String buildUrl(String path) {
        String base = appBaseUrl != null && !appBaseUrl.isBlank() ? appBaseUrl.trim() : "http://localhost:4200";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private static boolean isMovementsEnabled(Company company) {
        return company.getNotifMovements() == null || Boolean.TRUE.equals(company.getNotifMovements());
    }

    private static String normalizeMovementType(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String upper = code.trim().toUpperCase(Locale.ROOT);
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
        if ("Entrée".equalsIgnoreCase(code.trim())) {
            return "ENTREE";
        }
        if ("Sortie".equalsIgnoreCase(code.trim())) {
            return "SORTIE";
        }
        if ("Transfert".equalsIgnoreCase(code.trim())) {
            return "TRANSFERT";
        }
        if ("Ajustement".equalsIgnoreCase(code.trim())) {
            return "AJUSTEMENT";
        }
        return upper;
    }

    private static String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private record MovementSummary(long entries, long exits, long transfers, long adjustments) {
    }
}
