package com.stocksaas.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage en mémoire des tokens de téléchargement de facture (lien par email).
 * Les tokens expirent après 30 jours. En cas de redémarrage du serveur, les liens déjà envoyés ne fonctionneront plus.
 */
@Component
public class InvoiceDownloadTokenStore {

    private static final long VALIDITY_MS = 30L * 24 * 60 * 60 * 1000; // 30 jours

    /** token -> invoiceId */
    private final Map<String, Long> tokenToInvoiceId = new ConcurrentHashMap<>();
    /** token -> expiry timestamp */
    private final Map<String, Long> tokenExpiry = new ConcurrentHashMap<>();

    public String generateToken(Long invoiceId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToInvoiceId.put(token, invoiceId);
        tokenExpiry.put(token, System.currentTimeMillis() + VALIDITY_MS);
        return token;
    }

    public Long getInvoiceIdIfValid(String token) {
        if (token == null || token.isBlank()) return null;
        Long expiry = tokenExpiry.get(token);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            tokenToInvoiceId.remove(token);
            tokenExpiry.remove(token);
            return null;
        }
        return tokenToInvoiceId.get(token);
    }
}
