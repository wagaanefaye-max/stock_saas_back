package com.stocksaas.subscription;

/**
 * Calculs de tarification abonnement (montant mensuel configurable en base).
 */
public final class SubscriptionPricing {

    public static final double DEFAULT_MONTHLY_PRICE_FCFA = 5000.0;

    private SubscriptionPricing() {
    }

    /**
     * Montant total après réduction (arrondi à l'entier le plus proche).
     */
    public static double calculateTotal(int months, double discountPercent, double monthlyPriceFcfa) {
        if (months <= 0) {
            return 0;
        }
        double gross = monthlyPriceFcfa * months;
        double net = gross * (1 - discountPercent / 100.0);
        return Math.round(net);
    }

    public static double calculateGross(int months, double monthlyPriceFcfa) {
        return monthlyPriceFcfa * months;
    }
}
