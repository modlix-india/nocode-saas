package com.modlix.saas.adzump.platform;

import java.time.LocalDate;
import java.util.List;

import com.modlix.saas.adzump.model.leadzump.Grain;

/**
 * A metrics read at a specific grain. The grain is explicit because Meta in particular drops
 * adset/ad rows unless {@code ad_id}/{@code adset_id} are in the requested fields
 * (see reference_campaign_metrics_grains) — forcing the grain into the query keeps J10 from
 * silently getting zeroes.
 *
 * @param accountId the ad account to read from.
 * @param ids       the entity ids to scope to (campaign/adset/ad ids per grain; empty = account-wide).
 * @param from      inclusive start date.
 * @param to        inclusive end date.
 * @param grain     the grain rows are requested at (CAMPAIGN | ADSET | AD).
 * @param timezone  the report timezone (platform insights are timezone-sensitive).
 */
public record InsightQuery(
        String accountId,
        List<String> ids,
        LocalDate from,
        LocalDate to,
        Grain grain,
        String timezone) {

    public InsightQuery {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
