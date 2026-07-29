package com.modlix.saas.adzump.platform;

import com.modlix.saas.adzump.model.Money;

/**
 * A neutral bid instruction for {@link AdPlatform#updateBid}. P1 keeps this lean: a bid
 * {@code strategy} name (e.g. "MAXIMIZE_CONVERSIONS", "TARGET_CPA", "TARGET_ROAS") plus an optional
 * {@code target} amount (tCPA money, or a manual/cap bid). J3/J4 map these onto each platform's bid
 * strategy objects; a richer typed field set (tROAS as a ratio, cap vs target, portfolio strategy
 * ids) arrives with those slices.
 *
 * @param strategy platform-neutral bid strategy name.
 * @param target   optional target amount (tCPA / manual bid); null when the strategy needs none.
 */
public record BidSpec(String strategy, Money target) {
}
