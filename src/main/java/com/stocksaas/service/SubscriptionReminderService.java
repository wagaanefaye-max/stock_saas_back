package com.stocksaas.service;

import com.stocksaas.model.Company;
import com.stocksaas.model.SubscriptionReminderLog;
import com.stocksaas.model.User;
import com.stocksaas.repository.CompanyRepository;
import com.stocksaas.repository.SubscriptionReminderLogRepository;
import com.stocksaas.repository.UserRepository;
import com.stocksaas.subscription.SubscriptionReminderType;
import com.stocksaas.subscription.SubscriptionStatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionReminderService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SubscriptionReminderLogRepository reminderLogRepository;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:4200}")
    private String appBaseUrl;

    /**
     * Parcourt les entreprises et envoie les rappels J-7 et J-1 si nécessaire.
     */
    @Transactional
    public int processDueReminders() {
        LocalDate today = LocalDate.now();
        int sentCount = 0;
        List<Company> companies = companyRepository.findCompaniesWithTrialOrActiveSubscription();

        for (Company company : companies) {
            try {
                company = subscriptionService.syncSubscriptionStatus(company.getId());
            } catch (Exception e) {
                log.warn("Sync abonnement entreprise {} ignoré: {}", company.getId(), e.getMessage());
                continue;
            }

            if (SubscriptionStatusCode.EXPIRED.equals(company.getSubscriptionStatus())) {
                continue;
            }

            LocalDateTime periodEnd = resolvePeriodEnd(company);
            if (periodEnd == null || !periodEnd.isAfter(LocalDateTime.now())) {
                continue;
            }

            long daysUntilEnd = ChronoUnit.DAYS.between(today, periodEnd.toLocalDate());
            if (daysUntilEnd == 7) {
                sentCount += sendReminderIfNeeded(company, periodEnd, SubscriptionReminderType.DAYS_7, 7);
            } else if (daysUntilEnd == 1) {
                sentCount += sendReminderIfNeeded(company, periodEnd, SubscriptionReminderType.DAYS_1, 1);
            }
        }

        if (sentCount > 0) {
            log.info("Rappels abonnement envoyés: {}", sentCount);
        }
        return sentCount;
    }

    private LocalDateTime resolvePeriodEnd(Company company) {
        if (SubscriptionStatusCode.TRIAL.equals(company.getSubscriptionStatus())) {
            return company.getTrialEndsAt();
        }
        if (SubscriptionStatusCode.ACTIVE.equals(company.getSubscriptionStatus())) {
            return company.getSubscriptionEndsAt();
        }
        return null;
    }

    private int sendReminderIfNeeded(
            Company company,
            LocalDateTime periodEnd,
            String reminderType,
            long daysBeforeEnd
    ) {
        if (reminderLogRepository.existsByCompanyIdAndReminderTypeAndPeriodEndAt(
                company.getId(), reminderType, periodEnd)) {
            return 0;
        }

        Set<String> recipients = collectRecipientEmails(company);
        if (recipients.isEmpty()) {
            log.warn("Aucun e-mail pour rappel abonnement entreprise {} ({})", company.getId(), company.getName());
            return 0;
        }

        String kindLabel = SubscriptionStatusCode.TRIAL.equals(company.getSubscriptionStatus())
                ? "période d'essai gratuit"
                : "abonnement";
        String subscriptionsUrl = buildSubscriptionsUrl();
        List<User> admins = userRepository.findCompanyAdminsByCompanyId(company.getId());

        for (String email : recipients) {
            String recipientName = resolveRecipientName(admins, company.getName(), email);
            emailService.sendSubscriptionExpiryReminder(
                    email,
                    recipientName,
                    company.getName(),
                    kindLabel,
                    periodEnd,
                    daysBeforeEnd,
                    subscriptionsUrl
            );
        }

        SubscriptionReminderLog logEntry = new SubscriptionReminderLog();
        logEntry.setCompanyId(company.getId());
        logEntry.setReminderType(reminderType);
        logEntry.setPeriodEndAt(periodEnd);
        logEntry.setSentAt(LocalDateTime.now());
        logEntry.setSentToEmail(String.join(", ", recipients));
        reminderLogRepository.save(logEntry);

        return recipients.size();
    }

    private Set<String> collectRecipientEmails(Company company) {
        Set<String> emails = new LinkedHashSet<>();
        if (company.getEmail() != null && !company.getEmail().isBlank()) {
            emails.add(company.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        List<User> admins = userRepository.findCompanyAdminsByCompanyId(company.getId());
        for (User admin : admins) {
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

    private String buildSubscriptionsUrl() {
        String base = appBaseUrl != null && !appBaseUrl.isBlank() ? appBaseUrl.trim() : "http://localhost:4200";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/company-admin/subscriptions";
    }
}
