package com.stocksaas.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Génération de la référence produit au format explicite et optimisé :
 * REF_YYYYMMDD_SKU_ID
 * - YYYYMMDD : date de création (tri chronologique, index-friendly)
 * - SKU : slug du SKU (alphanumérique, max 12 caractères) pour identification rapide
 * - ID : identifiant unique en base
 * Exemple : REF_20260213_TELSAMGAL01_1
 */
public final class ProductReferenceUtil {

    private static final int SKU_SLUG_MAX_LEN = 12;
    private static final String PREFIX = "REF_";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private ProductReferenceUtil() {}

    /**
     * Construit la référence à partir de la date, du SKU et de l'ID.
     * Utilisé à la création du produit (après le premier save).
     */
    public static String buildReference(LocalDate date, String sku, Long id) {
        String datePart = date.format(DATE_FORMAT);
        String slug = slugFromSku(sku);
        return PREFIX + datePart + "_" + slug + "_" + id;
    }

    /**
     * Référence de secours pour les anciens produits sans référence stockée
     * (même format mais sans SKU : REF_YYYYMMDD_ID).
     */
    public static String buildFallbackReference(LocalDate date, Long id) {
        return PREFIX + date.format(DATE_FORMAT) + "_" + id;
    }

    /**
     * Slug du SKU : majuscules, alphanumériques uniquement, max 12 caractères.
     * Ex. "TEL-SAM-GAL-001" -> "TELSAMGAL001"
     */
    public static String slugFromSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return "P";
        }
        String cleaned = sku.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (cleaned.isEmpty()) {
            return "P";
        }
        return cleaned.length() > SKU_SLUG_MAX_LEN
                ? cleaned.substring(0, SKU_SLUG_MAX_LEN)
                : cleaned;
    }
}
