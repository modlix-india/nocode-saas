package com.modlix.saas.adzump.service.creative;

import java.util.OptionalDouble;

import org.springframework.stereotype.Component;

/**
 * The default {@link MarketPriors} binding: no priors (J19 not built). Attribution and prediction then
 * shrink toward the account's own baseline / vertical defaults rather than any market seed. J19 will
 * replace this with a real cross-account priors source; the seam is stable across that change.
 */
@Component
public class EmptyMarketPriors implements MarketPriors {

    @Override
    public OptionalDouble priorScore(String vertical, String axis, String value) {
        return OptionalDouble.empty();
    }

    @Override
    public OptionalDouble priorBaseline(String vertical) {
        return OptionalDouble.empty();
    }
}
