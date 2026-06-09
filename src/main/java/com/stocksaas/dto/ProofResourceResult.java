package com.stocksaas.dto;

import org.springframework.core.io.Resource;

/**
 * Ressource justificatif + type MIME pour la réponse HTTP.
 */
public record ProofResourceResult(Resource resource, String contentType) {}
