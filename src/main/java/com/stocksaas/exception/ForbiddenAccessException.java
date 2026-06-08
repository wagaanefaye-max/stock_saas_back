package com.stocksaas.exception;

/**
 * Accès refusé (403) — ressource ou action non autorisée pour l'utilisateur connecté.
 */
public class ForbiddenAccessException extends RuntimeException {

    public ForbiddenAccessException(String message) {
        super(message);
    }
}
