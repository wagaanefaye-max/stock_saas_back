package com.stocksaas.scheduler;

import com.stocksaas.service.EmailOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRetryScheduler {

    private final EmailOutboxService emailOutboxService;

    @Value("${app.mail.retry.enabled:true}")
    private boolean retryEnabled;

    /** Toutes les 10 minutes par défaut. */
    @Scheduled(cron = "${app.mail.retry.cron:0 */10 * * * *}")
    public void retryPendingEmails() {
        if (!retryEnabled) {
            return;
        }
        try {
            int processed = emailOutboxService.processDueRetries();
            if (processed > 0) {
                log.info("Relance e-mails : {} message(s) traité(s)", processed);
            }
        } catch (Exception e) {
            log.error("Échec du job de relance e-mails : {}", e.getMessage(), e);
        }
    }
}
