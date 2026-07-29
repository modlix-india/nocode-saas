package com.modlix.saas.adzump.platform.google;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.google.ads.googleads.v24.services.GoogleAdsRow;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.platform.InsightQuery;
import com.modlix.saas.adzump.platform.PlatformInsight;
import com.modlix.saas.adzump.platform.PlatformRef;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;

/**
 * The Google read side (J4 §5.5): {@link #insights} issues one GAQL query per {@link InsightQuery}
 * grain and maps the rows to platform-neutral {@link PlatformInsight}s. Unlike Meta, the grain <b>is
 * the GAQL {@code FROM} resource</b> (campaign / ad_group / ad_group_ad), so there is no
 * fields-gotcha that silently zeroes rows — but the grain→resource map is the thing to get right, and
 * it lives in exactly one place ({@link #resourceForGrain}). Cost comes back in micros and is
 * converted to major units; Google reports in the account's own timezone natively, so the query
 * timezone is not injected into the GAQL (a Meta-only concern).
 */
@Component
public class GoogleGaqlReader {

    private final GoogleAdsClientFacade facade;
    private final AdzumpMessageResourceService msg;

    public GoogleGaqlReader(GoogleAdsClientFacade facade, AdzumpMessageResourceService msg) {
        this.facade = facade;
        this.msg = msg;
    }

    public List<PlatformInsight> insights(Token t, InsightQuery q) {
        String customerId = GoogleTokens.requireCustomerIdString(t, this.msg);
        String gaql = buildQuery(q);

        List<PlatformInsight> out = new ArrayList<>();
        for (GoogleAdsRow row : this.facade.search(t, customerId, gaql))
            out.add(toInsight(q.grain(), row));
        return out;
    }

    /** The single grain→GAQL-resource map: CAMPAIGN→campaign, ADSET→ad_group, AD→ad_group_ad. */
    static String resourceForGrain(Grain grain) {
        return switch (grain) {
            case CAMPAIGN -> "campaign";
            case ADSET -> "ad_group";
            case AD -> "ad_group_ad";
        };
    }

    /** The grain's own id field, used both in SELECT and (when ids are given) the WHERE filter. */
    private static String idFieldForGrain(Grain grain) {
        return switch (grain) {
            case CAMPAIGN -> "campaign.id";
            case ADSET -> "ad_group.id";
            case AD -> "ad_group_ad.ad.id";
        };
    }

    /** Builds the GAQL for a query. Package-visible so the grain→resource map is directly asserted. */
    String buildQuery(InsightQuery q) {
        Grain grain = q.grain();

        StringBuilder select = new StringBuilder("SELECT ");
        // Identity fields per grain (parent ids included so a row can be attributed up the tree).
        select.append(switch (grain) {
            case CAMPAIGN -> "campaign.id";
            case ADSET -> "ad_group.id, campaign.id";
            case AD -> "ad_group_ad.ad.id, ad_group.id, campaign.id";
        });
        select.append(", customer.currency_code, metrics.impressions, metrics.clicks, metrics.cost_micros, ")
                .append("metrics.conversions, segments.date");

        StringBuilder gaql = new StringBuilder(select)
                .append(" FROM ").append(resourceForGrain(grain))
                .append(" WHERE segments.date BETWEEN '").append(q.from()).append("' AND '").append(q.to()).append('\'');

        if (!q.ids().isEmpty())
            gaql.append(" AND ").append(idFieldForGrain(grain)).append(" IN (").append(inList(q.ids())).append(')');

        return gaql.toString();
    }

    private static String inList(List<String> ids) {
        // Google ids are numeric — emit unquoted for the integer id columns.
        return String.join(", ", ids.stream().map(GoogleTokens::digits).toList());
    }

    private PlatformInsight toInsight(Grain grain, GoogleAdsRow row) {
        PlatformRef ref = switch (grain) {
            case CAMPAIGN -> new PlatformRef("campaign", Long.toString(row.getCampaign().getId()));
            case ADSET -> new PlatformRef("adSet", Long.toString(row.getAdGroup().getId()));
            case AD -> new PlatformRef("ad", Long.toString(row.getAdGroupAd().getAd().getId()));
        };

        long costMicros = row.getMetrics().getCostMicros();
        Money spend = new Money()
                .setAmount(BigDecimal.valueOf(costMicros, 6)) // micros → major units (scale 6)
                .setCurrency(row.getCustomer().getCurrencyCode());

        return new PlatformInsight(
                grain,
                ref,
                row.getMetrics().getImpressions(),
                row.getMetrics().getClicks(),
                spend,
                (long) row.getMetrics().getConversions());
    }
}
