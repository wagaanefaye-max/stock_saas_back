package com.stocksaas.exception;

/**
 * Levée lorsqu'une entreprise en mode lecture seule tente une opération d'écriture.
 */
public class SubscriptionReadOnlyException extends RuntimeException {

    public SubscriptionReadOnlyException(String message) {
        super(message);
    }
}
