package com.stocksaas.exception;

import java.time.LocalDateTime;

/**
 * Compte utilisateur temporairement bloqué après trop de tentatives de connexion.
 */
public class AccountLockedException extends RuntimeException {

    private final LocalDateTime lockedUntil;

    public AccountLockedException(String message, LocalDateTime lockedUntil) {
        super(message);
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
