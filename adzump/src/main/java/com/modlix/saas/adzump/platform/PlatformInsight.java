package com.modlix.saas.adzump.platform;

import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.Grain;

/**
 * One metrics row at a grain: the platform-reported fast signal the loop pairs with CRM outcomes
 * (J10). Platform conversions are the platform's own attributed conversions and are kept distinct
 * from CRM-attributed outcomes, which are joined in separately downstream.
 *
 * @param grain               the grain this row is at.
 * @param ref                 the entity this row is for.
 * @param impressions         reported impressions.
 * @param clicks              reported clicks.
 * @param spend               reported spend.
 * @param platformConversions the platform's own attributed conversions.
 */
public record PlatformInsight(
        Grain grain,
        PlatformRef ref,
        long impressions,
        long clicks,
        Money spend,
        long platformConversions) {
}
