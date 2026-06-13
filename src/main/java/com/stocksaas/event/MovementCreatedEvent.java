package com.stocksaas.event;

/**
 * Émis après validation d'un mouvement de stock (post-commit).
 */
public record MovementCreatedEvent(Long movementId) {
}
