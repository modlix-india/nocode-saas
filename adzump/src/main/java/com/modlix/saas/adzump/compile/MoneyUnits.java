package com.modlix.saas.adzump.compile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

import com.modlix.saas.adzump.model.Money;

/**
 * Major-unit → platform-unit money conversion (CONTRACT §0: the IR carries <b>major</b> units, the
 * compiler converts to each platform's convention).
 *
 * <ul>
 * <li><b>Meta</b> — account-currency <i>minor</i> units. INR/USD (2 fraction digits) ⇒ {@code amount
 * * 100}; JPY (0) ⇒ {@code amount * 1}. The multiplier is derived from the ISO-4217 currency, never
 * hardcoded per currency.</li>
 * <li><b>Google</b> — <i>micros</i>: {@code amount * 1_000_000} for every currency.</li>
 * </ul>
 *
 * <p>
 * A {@code null} {@link Money} or amount, or a currency ISO-4217 code the JVM does not recognise, is
 * a hard failure (fail-fast) — not a silent default. A missing required amount is a J6 validation
 * failure that never reaches compile; if one does, this throws rather than inventing a value.
 * </p>
 */
public final class MoneyUnits {

    private static final BigDecimal MICROS_MULTIPLIER = BigDecimal.valueOf(1_000_000L);

    private MoneyUnits() {
    }

    /** Meta: account-currency minor units, currency-aware. */
    public static long toMinorUnits(Money money) {
        BigDecimal amount = requireAmount(money);
        int fractionDigits = fractionDigits(money.getCurrency());
        return amount.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Google: micros ({@code amount * 1_000_000}). */
    public static long toMicros(Money money) {
        BigDecimal amount = requireAmount(money);
        return amount.multiply(MICROS_MULTIPLIER).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal requireAmount(Money money) {
        if (money == null || money.getAmount() == null)
            throw new IllegalStateException("Money amount is required for compilation (J6 gates this upstream)");
        return money.getAmount();
    }

    private static int fractionDigits(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank())
            throw new IllegalStateException("Money currency is required for compilation");
        int digits = Currency.getInstance(currencyCode.trim()).getDefaultFractionDigits();
        if (digits < 0)
            throw new IllegalStateException("Currency has no minor unit: " + currencyCode);
        return digits;
    }
}
