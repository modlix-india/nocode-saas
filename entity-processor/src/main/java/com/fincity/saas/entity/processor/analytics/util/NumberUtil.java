package com.fincity.saas.entity.processor.analytics.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NumberUtil {

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

    @SuppressWarnings("unchecked")
    public static <T extends Number> T add(T a, T b) {
        if (a == null || b == null) return null;

        if (a instanceof Double || b instanceof Double) {
            return (T) Double.valueOf(a.doubleValue() + b.doubleValue());
        } else if (a instanceof Float || b instanceof Float) {
            return (T) Float.valueOf(a.floatValue() + b.floatValue());
        } else if (a instanceof Long || b instanceof Long) {
            return (T) Long.valueOf(a.longValue() + b.longValue());
        } else {
            return (T) Integer.valueOf(a.intValue() + b.intValue());
        }
    }
}
