package com.stocksaas.scheduler;

import com.stocksaas.service.SubscriptionReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionReminderScheduler {

    private final SubscriptionReminderService subscriptionReminderService;

    @Value("${app.subscription.reminders.enabled:true}")
    private boolean remindersEnabled;

    /** Tous les jours à 08:00 (heure serveur). */
    @Scheduled(cron = "${app.subscription.reminders.cron:0 0 8 * * *}")
    public void sendSubscriptionExpiryReminders() {
        if (!remindersEnabled) {
            return;
        }
        try {
            log.debug("Exécution des rappels d'expiration d'abonnement…");
            subscriptionReminderService.processDueReminders();
        } catch (Exception e) {
            log.error("Échec du job de rappels abonnement: {}", e.getMessage(), e);
        }
    }
}
