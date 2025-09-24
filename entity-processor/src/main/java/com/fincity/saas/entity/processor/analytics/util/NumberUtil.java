package com.fincity.saas.entity.processor.analytics.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumberUtil {

    private NumberUtil() {}

    public static Double getPercentage(Number part, Number whole, int roundPlace) {

        if (whole == null || part == null) return 0.00d;

        return getPercentage(part.doubleValue(), whole.doubleValue(), roundPlace);
    }

    public static Double getPercentage(Double part, Double whole, int roundPlace) {

        if (whole == null || part == null) return 0.00d;

        if (whole == 0) return 0.00d;

        Double percentage = 100 * part / whole;
        return round(percentage, roundPlace);
    }

    public static Double round(Double value, int places) {

        if (places < 0) return null;

        BigDecimal bd = BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
