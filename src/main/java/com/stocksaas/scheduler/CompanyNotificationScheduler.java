package com.stocksaas.scheduler;

import com.stocksaas.service.CompanyNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyNotificationScheduler {

    private final CompanyNotificationService companyNotificationService;

    @Value("${app.notifications.low-stock.enabled:true}")
    private boolean lowStockEnabled;

    @Value("${app.notifications.weekly-report.enabled:true}")
    private boolean weeklyReportEnabled;

    /** Digest stock bas — tous les jours à 08:15. */
    @Scheduled(cron = "${app.notifications.low-stock.cron:0 15 8 * * *}")
    public void sendLowStockDigests() {
        if (!lowStockEnabled) {
            return;
        }
        try {
            companyNotificationService.sendDailyLowStockDigests();
        } catch (Exception e) {
            log.error("Échec du job digest stock bas: {}", e.getMessage(), e);
        }
    }

    /** Résumé hebdomadaire — chaque lundi à 08:30. */
    @Scheduled(cron = "${app.notifications.weekly-report.cron:0 30 8 * * MON}")
    public void sendWeeklyReports() {
        if (!weeklyReportEnabled) {
            return;
        }
        try {
            companyNotificationService.sendWeeklyReports();
        } catch (Exception e) {
            log.error("Échec du job résumé hebdomadaire: {}", e.getMessage(), e);
        }
    }
}
