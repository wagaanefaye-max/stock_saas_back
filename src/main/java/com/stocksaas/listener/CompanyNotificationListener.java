package com.stocksaas.listener;

import com.stocksaas.event.MovementCreatedEvent;
import com.stocksaas.service.CompanyNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CompanyNotificationListener {

    private final CompanyNotificationService companyNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMovementCreated(MovementCreatedEvent event) {
        companyNotificationService.sendMovementNotification(event.movementId());
    }
}
