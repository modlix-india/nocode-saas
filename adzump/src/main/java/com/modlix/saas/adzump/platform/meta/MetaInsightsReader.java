package com.modlix.saas.adzump.platform.meta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.platform.InsightQuery;
import com.modlix.saas.adzump.platform.PlatformInsight;
import com.modlix.saas.adzump.platform.PlatformRef;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * J3 — the Meta Insights read at a grain (the loop's fast signal, J10). Encapsulates the one gotcha
 * that silently zeroed the legacy: <b>adset/ad rows come back empty unless the Insights call both
 * sets {@code level} to the grain AND names {@code adset_id}/{@code ad_id} explicitly in the requested
 * fields</b> (see reference_campaign_metrics_grains). This reader always does both, per grain, so J10
 * never gets zeroes it should not.
 *
 * <p>
 * The date range is passed as {@code time_range}; the account's configured attribution is honored via
 * {@code use_unified_attribution_setting}. Meta Insights has no timezone request parameter — rows are
 * always returned in the <b>ad account's</b> reporting timezone, so {@link InsightQuery#timezone()} is
 * the caller's record of that account tz (the loop runs in it), not a value sent on the wire.
 * </p>
 */
@Component
public class MetaInsightsReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MetaGraphClient graph;
    private final AdzumpMessageResourceService msgService;

    public MetaInsightsReader(MetaGraphClient graph, AdzumpMessageResourceService msgService) {
        this.graph = graph;
        this.msgService = msgService;
    }

    public List<PlatformInsight> insights(Token token, InsightQuery query) {

        if (query == null || query.grain() == null)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "insightQuery");

        Grain grain = query.grain();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("level", metaLevel(grain));                 // level MUST match the grain...
        params.put("fields", fieldsFor(grain));                // ...and the id fields MUST be explicit.
        params.put("time_range", timeRange(query));
        // Honor the ad set's configured attribution window (Meta's per-account/unified setting).
        params.put("use_unified_attribution_setting", "true");
        params.put("limit", "500");

        String filtering = filtering(grain, query.ids());
        if (filtering != null)
            params.put("filtering", filtering);

        JsonNode response = this.graph.get(token, accountNode(query.accountId()) + "/insights", params);

        List<PlatformInsight> rows = new ArrayList<>();
        JsonNode data = response == null ? null : response.get("data");
        if (data != null && data.isArray())
            for (JsonNode row : data)
                rows.add(toInsight(grain, row));

        return rows;
    }

    // --- request shaping ------------------------------------------------------------------------

    private static String metaLevel(Grain grain) {
        return switch (grain) {
            case CAMPAIGN -> "campaign";
            case ADSET -> "adset";
            case AD -> "ad";
        };
    }

    /**
     * The requested fields per grain. campaign_id is always requested; adset_id is added at ADSET and
     * AD; ad_id is added at AD. Without these explicit id fields Meta returns the finer rows as zero.
     */
    private static String fieldsFor(Grain grain) {
        StringBuilder fields = new StringBuilder("impressions,clicks,spend,actions,account_currency,campaign_id");
        if (grain == Grain.ADSET || grain == Grain.AD)
            fields.append(",adset_id");
        if (grain == Grain.AD)
            fields.append(",ad_id");
        return fields.toString();
    }

    private String timeRange(InsightQuery query) {
        if (query.from() == null || query.to() == null)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "dateRange");
        return json(Map.of("since", query.from().toString(), "until", query.to().toString()));
    }

    /** Scopes the read to the requested ids at the grain's id field; null (account-wide) when empty. */
    private String filtering(Grain grain, List<String> ids) {
        if (ids == null || ids.isEmpty())
            return null;
        String field = switch (grain) {
            case CAMPAIGN -> "campaign.id";
            case ADSET -> "adset.id";
            case AD -> "ad.id";
        };
        return json(List.of(Map.of("field", field, "operator", "IN", "value", ids)));
    }

    // --- response parsing -----------------------------------------------------------------------

    private PlatformInsight toInsight(Grain grain, JsonNode row) {

        String id = switch (grain) {
            case CAMPAIGN -> text(row, "campaign_id");
            case ADSET -> text(row, "adset_id");
            case AD -> text(row, "ad_id");
        };

        Money spend = new Money(decimal(row, "spend"), text(row, "account_currency"));

        return new PlatformInsight(
                grain,
                new PlatformRef(entityType(grain), id),
                longValue(row, "impressions"),
                longValue(row, "clicks"),
                spend,
                leadConversions(row.get("actions")));
    }

    /** Platform-attributed lead conversions from the {@code actions} breakdown (kept distinct from CRM). */
    private static long leadConversions(JsonNode actions) {
        if (actions == null || !actions.isArray())
            return 0L;
        long total = 0L;
        for (JsonNode action : actions) {
            String type = text(action, "action_type");
            if (type != null && type.toLowerCase().contains("lead")) {
                JsonNode value = action.get("value");
                if (value != null && !value.isNull())
                    total += Math.round(value.asDouble());
            }
        }
        return total;
    }

    private static String entityType(Grain grain) {
        return switch (grain) {
            case CAMPAIGN -> "campaign";
            case ADSET -> "adSet";
            case AD -> "ad";
        };
    }

    // --- helpers --------------------------------------------------------------------------------

    private String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
                    AdzumpMessageResourceService.META_API_ERROR, "insight query encoding");
        }
    }

    private String accountNode(String accountId) {
        if (accountId == null || accountId.isBlank())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "accountId");
        return accountId.startsWith("act_") ? accountId : "act_" + accountId;
    }

    private static String text(JsonNode node, String field) {
        if (node == null)
            return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull())
            return 0L;
        // Meta reports numeric metrics as strings; asLong on a textual node is 0, so parse the text.
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText().trim());
            } catch (NumberFormatException e) {
                return Math.round(Double.parseDouble(value.asText().trim()));
            }
        }
        return value.asLong();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull())
            return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.asText().trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
