package com.stocksaas.dto;

/**
 * Fichier justificatif stocké (disque + copie en base pour persistance).
 */
public record StoredProofFile(
        String filePath,
        byte[] data,
        String contentType
) {}
