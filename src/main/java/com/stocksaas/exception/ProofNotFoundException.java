package com.stocksaas.exception;

/**
 * Justificatif de souscription introuvable ou illisible.
 */
public class ProofNotFoundException extends RuntimeException {

    public ProofNotFoundException(String message) {
        super(message);
    }
}
