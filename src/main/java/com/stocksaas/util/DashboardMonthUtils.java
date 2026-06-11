package com.stocksaas.util;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Libellés de mois et séries temporelles pour les graphiques du dashboard.
 */
public final class DashboardMonthUtils {

    private static final String[] MONTH_LABELS =
            {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};

    private DashboardMonthUtils() {
    }

    public static String label(YearMonth yearMonth) {
        return MONTH_LABELS[yearMonth.getMonthValue() - 1];
    }

    /** Les 6 derniers mois, du plus ancien au plus récent. */
    public static List<YearMonth> lastSixMonthsChronological() {
        YearMonth current = YearMonth.now();
        List<YearMonth> months = new ArrayList<>(6);
        for (int i = 5; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }
        return months;
    }
}
