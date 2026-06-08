package com.stocksaas.subscription;

public final class PaymentProviderCode {

    public static final String WAVE = "WAVE";
    public static final String ORANGE_MONEY = "ORANGE_MONEY";
    /** Réservé — pas encore accepté à la soumission */
    public static final String CASH = "CASH";

    private PaymentProviderCode() {
    }

    public static boolean isValid(String code) {
        return WAVE.equals(code) || ORANGE_MONEY.equals(code);
    }

    public static String label(String code) {
        if (WAVE.equals(code)) {
            return "Wave";
        }
        if (ORANGE_MONEY.equals(code)) {
            return "Orange Money";
        }
        if (CASH.equals(code)) {
            return "Espèce";
        }
        return code;
    }
}
