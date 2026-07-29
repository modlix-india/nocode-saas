package com.modlix.saas.adzump.platform.meta;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.modlix.saas.adzump.compile.MoneyUnits;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.platform.BidSpec;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.platform.CompiledCreative;
import com.modlix.saas.adzump.platform.CreativeRef;
import com.modlix.saas.adzump.platform.LaunchResult;
import com.modlix.saas.adzump.platform.PlatformRef;
import com.modlix.saas.adzump.platform.RunState;
import com.modlix.saas.adzump.platform.TargetingPatch;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * J3 — the Meta create/mutate sequencer. It reads the {@link CompiledCampaign} payload tree that J7
 * built and turns it into the ordered Graph create-calls, then owns the mutation edges the loop (J8/
 * J13) drives after launch.
 *
 * <p>
 * <b>launchPaused</b> is the load-bearing method:
 * </p>
 * <ol>
 * <li><b>Pre-flight compliance (reject, do not repair):</b> when the compiled payload declares a
 * required special-ad-category (e.g. real-estate → HOUSING), the campaign body must carry it in
 * {@code special_ad_categories} <i>before</i> any API call. A payload that requires HOUSING but does
 * not declare it is rejected here — we never let Meta bounce it, and never launch it non-compliant
 * (the legacy quality bug).</li>
 * <li><b>Ordered create, all PAUSED:</b> campaign → (lead form on the page, when the type is LEADS) →
 * ad set(s) → creative(s) → ad(s). Order matters: the lead form and the creative must exist before
 * the ad that references them, so the freshly-created form id is rebound onto the ad's
 * call-to-action.</li>
 * <li><b>Do-no-harm rollback:</b> a mid-sequence Graph failure deletes every object created <i>in
 * this call</i> (LIFO) and returns a {@link LaunchResult#failed}. Nothing is left half-built, and
 * because every object was created PAUSED there is never a live/ACTIVE remnant. Errors are returned,
 * not thrown, so J8's partial-failure fan-out (Meta + Google) can isolate one leg.</li>
 * </ol>
 *
 * <p>The token/credentials come from J2; this class never resolves its own. The low-level HTTP is
 * {@link MetaGraphClient}, mocked in every test.</p>
 */
@Component
public class MetaLifecycle {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> BODY_MAP = new TypeReference<>() {
    };

    private final MetaGraphClient graph;
    private final AdzumpMessageResourceService msgService;

    public MetaLifecycle(MetaGraphClient graph, AdzumpMessageResourceService msgService) {
        this.graph = graph;
        this.msgService = msgService;
    }

    // =====================================================================================
    // Launch — the ordered, paused, rolled-back-on-failure create sequence
    // =====================================================================================

    public LaunchResult launchPaused(CompiledCampaign compiled, Token token) {

        JsonNode payload = compiled == null ? null : compiled.payload();
        if (payload == null)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "compiledPayload");

        // (1) Pre-flight compliance — rejected BEFORE any create (throws; nothing to roll back).
        assertCompliance(payload);

        String account = accountNode(token);
        // LIFO of created platform ids, so rollback deletes children before parents.
        Deque<String> created = new ArrayDeque<>();

        try {
            // (2a) Campaign.
            JsonNode campaignNode = requireObject(payload, "campaign");
            String campaignId = createNode(token, account + "/campaigns", bodyOf(campaignNode));
            created.push(campaignId);

            // (2b) Lead form(s) on the page, BEFORE the ad (LEADS only). Map compiled form name → real id.
            Map<String, String> formIdByName = new HashMap<>();
            boolean leadGen = compiled.type() == CampaignType.LEADS;
            JsonNode leadForms = payload.get("leadForms");
            if (leadGen && leadForms != null && leadForms.isArray() && !leadForms.isEmpty()) {
                String pageId = requirePageId(payload);
                for (JsonNode form : leadForms) {
                    String formId = createNode(token, pageId + "/leadgen_forms", bodyOf(form));
                    created.push(formId);
                    JsonNode name = form.get("name");
                    if (name != null && !name.isNull())
                        formIdByName.put(name.asText(), formId);
                }
            }

            // (2c) Ad set(s) → creative(s) → ad(s), each PAUSED.
            JsonNode adSets = payload.get("adSets");
            if (adSets != null && adSets.isArray())
                for (JsonNode adSet : adSets) {

                    Map<String, Object> adSetBody = bodyOfExcluding(adSet, "ads");
                    adSetBody.put("campaign_id", campaignId);
                    String adSetId = createNode(token, account + "/adsets", adSetBody);
                    created.push(adSetId);

                    JsonNode ads = adSet.get("ads");
                    if (ads != null && ads.isArray())
                        for (JsonNode ad : ads) {

                            // Creative first: rebind the placeholder lead-form id to the created one.
                            JsonNode creativeNode = ad.get("creative");
                            rebindLeadForm(creativeNode, formIdByName);
                            String creativeId = createNode(token, account + "/adcreatives", bodyOf(creativeNode));
                            created.push(creativeId);

                            // Then the ad, referencing the creative + ad set, PAUSED.
                            Map<String, Object> adBody = new LinkedHashMap<>();
                            adBody.put("name", text(ad, "name"));
                            adBody.put("adset_id", adSetId);
                            adBody.put("status", "PAUSED");
                            adBody.put("creative", Map.of("creative_id", creativeId));
                            String adId = createNode(token, account + "/ads", adBody);
                            created.push(adId);
                        }
                }

            // (3) Success — hand the created ids back as a Links fragment for J8 to persist on plan.links.
            Links links = new Links().setMeta(new Links.MetaLinks()
                    .setAdAccountId(token == null ? null : token.accountId())
                    .setPageId(pageId(payload))
                    .setPixelId(pixelId(payload))
                    .setCampaignId(campaignId));
            return LaunchResult.ok(Platform.META, links);

        } catch (RuntimeException e) {
            // Do-no-harm: undo everything created in this call, then report failure (never throw a
            // half-built campaign up to J8; everything is paused regardless).
            rollback(token, created);
            String reason = e instanceof GenericException ge ? ge.getMessage()
                    : (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return LaunchResult.failed(Platform.META, reason);
        }
    }

    // =====================================================================================
    // Mutations — the SPI lifecycle edges (budget at campaign CBO or ad set per the compiled plan)
    // =====================================================================================

    public void setStatus(Token token, PlatformRef ref, RunState state) {
        this.graph.post(token, ref.id(), Map.of("status", metaStatus(state)));
    }

    public void updateBudget(Token token, PlatformRef ref, Money daily) {
        // The caller (J8) passes the ref of the budget-bearing object — campaign for CBO, ad set for ABO.
        this.graph.post(token, ref.id(), Map.of("daily_budget", MoneyUnits.toMinorUnits(daily)));
    }

    public void updateBid(Token token, PlatformRef ref, BidSpec bid) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bid_strategy", metaBidStrategy(bid.strategy()));
        if (bid.target() != null)
            body.put("bid_amount", MoneyUnits.toMinorUnits(bid.target()));
        this.graph.post(token, ref.id(), body);
    }

    public void mutateTargeting(Token token, PlatformRef adSet, TargetingPatch patch) {
        this.graph.post(token, adSet.id(), Map.of("targeting", patch.patch()));
    }

    public CreativeRef upsertCreative(Token token, PlatformRef adSet, CompiledCreative creative) {
        // Creatives are created under the account, then referenced by an ad (J8 wires the ad).
        String creativeId = createNode(token, accountNode(token) + "/adcreatives", bodyOf(creative.payload()));
        return new CreativeRef(creativeId);
    }

    // =====================================================================================
    // Compliance pre-flight
    // =====================================================================================

    /**
     * Rejects a payload that must declare a special ad category but does not. J7 stamps the resolved
     * requirement into {@code payload.compliance.requiredSpecialAdCategory}; the campaign body carries
     * the actual {@code special_ad_categories}. If the two disagree (a required category is missing),
     * we reject pre-flight — before any Graph call — rather than launching a non-compliant campaign.
     */
    private void assertCompliance(JsonNode payload) {

        JsonNode compliance = payload.get("compliance");
        if (compliance == null)
            return;

        String required = text(compliance, "requiredSpecialAdCategory");
        if (required == null || required.isBlank() || "NONE".equalsIgnoreCase(required))
            return;

        boolean declared = false;
        JsonNode campaign = payload.get("campaign");
        JsonNode categories = campaign == null ? null : campaign.get("special_ad_categories");
        if (categories != null && categories.isArray())
            for (JsonNode c : categories)
                if (required.equalsIgnoreCase(c.asText())) {
                    declared = true;
                    break;
                }

        if (!declared)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.UNPROCESSABLE_ENTITY, msg),
                    AdzumpMessageResourceService.SPECIAL_CATEGORY_REQUIRED, required);
    }

    // =====================================================================================
    // Graph helpers
    // =====================================================================================

    private String createNode(Token token, String edge, Map<String, ?> body) {
        JsonNode response = this.graph.post(token, edge, body);
        JsonNode id = response == null ? null : response.get("id");
        if (id == null || id.isNull() || id.asText().isBlank())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                    AdzumpMessageResourceService.META_API_ERROR, "no id returned from " + edge);
        return id.asText();
    }

    private void rollback(Token token, Deque<String> createdIds) {
        while (!createdIds.isEmpty()) {
            String id = createdIds.pop();
            try {
                this.graph.delete(token, id);
            } catch (RuntimeException ignore) {
                // Best-effort cleanup: a delete failing must not mask the original launch failure.
                // Everything is PAUSED, so a residual object never spends.
            }
        }
    }

    private static void rebindLeadForm(JsonNode creativeNode, Map<String, String> formIdByName) {
        if (creativeNode == null || formIdByName.isEmpty())
            return;
        JsonNode oss = creativeNode.get("object_story_spec");
        JsonNode linkData = oss == null ? null : oss.get("link_data");
        JsonNode cta = linkData == null ? null : linkData.get("call_to_action");
        JsonNode value = cta == null ? null : cta.get("value");
        if (value == null || !value.isObject())
            return;
        JsonNode current = value.get("lead_gen_form_id");
        if (current == null || current.isNull())
            return;
        String realId = formIdByName.get(current.asText());
        if (realId != null)
            ((ObjectNode) value).put("lead_gen_form_id", realId);
    }

    // =====================================================================================
    // Payload / enum mapping
    // =====================================================================================

    private static Map<String, Object> bodyOf(JsonNode node) {
        if (node == null || !node.isObject())
            return new LinkedHashMap<>();
        return MAPPER.convertValue(node, BODY_MAP);
    }

    private static Map<String, Object> bodyOfExcluding(JsonNode node, String... exclude) {
        Map<String, Object> body = bodyOf(node);
        for (String key : exclude)
            body.remove(key);
        return body;
    }

    private String requirePageId(JsonNode payload) {
        String pageId = pageId(payload);
        if (pageId == null || pageId.isBlank())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.UNPROCESSABLE_ENTITY, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "meta.pageId");
        return pageId;
    }

    /** The page id the ads promote / the lead form is created on: from the first ad set's promoted_object. */
    private static String pageId(JsonNode payload) {
        JsonNode adSets = payload.get("adSets");
        if (adSets != null && adSets.isArray())
            for (JsonNode adSet : adSets) {
                JsonNode promoted = adSet.get("promoted_object");
                String pid = text(promoted, "page_id");
                if (pid != null)
                    return pid;
                // Fallback: the creative's object_story_spec also carries the page id.
                JsonNode ads = adSet.get("ads");
                if (ads != null && ads.isArray())
                    for (JsonNode ad : ads) {
                        JsonNode oss = ad.path("creative").path("object_story_spec");
                        String fromOss = text(oss, "page_id");
                        if (fromOss != null)
                            return fromOss;
                    }
            }
        return null;
    }

    private static String pixelId(JsonNode payload) {
        JsonNode adSets = payload.get("adSets");
        if (adSets != null && adSets.isArray())
            for (JsonNode adSet : adSets) {
                String px = text(adSet.get("promoted_object"), "pixel_id");
                if (px != null)
                    return px;
            }
        return null;
    }

    private JsonNode requireObject(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isObject())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, field);
        return node;
    }

    private static String text(JsonNode node, String field) {
        if (node == null)
            return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String metaStatus(RunState state) {
        return switch (state) {
            case PAUSE -> "PAUSED";
            case ACTIVE -> "ACTIVE";
            case ARCHIVED -> "ARCHIVED";
        };
    }

    /** Neutral bid-strategy token → Meta enum (mirrors the compiler's mapping for the live-edit path). */
    private String metaBidStrategy(String neutral) {
        if (neutral == null || neutral.isBlank())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "bidStrategy");
        return switch (neutral.trim().toUpperCase()) {
            case "MAXIMIZE_CONVERSIONS", "MAXIMIZE_REACH", "LOWEST_COST", "AUTOMATIC", "LOWEST_COST_WITHOUT_CAP" ->
                "LOWEST_COST_WITHOUT_CAP";
            case "TARGET_CPA", "COST_CAP" -> "COST_CAP";
            case "BID_CAP", "LOWEST_COST_WITH_BID_CAP" -> "LOWEST_COST_WITH_BID_CAP";
            case "MIN_ROAS", "TARGET_ROAS", "LOWEST_COST_WITH_MIN_ROAS" -> "LOWEST_COST_WITH_MIN_ROAS";
            default -> this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "bidStrategy:" + neutral);
        };
    }

    /** The account node ({@code act_...}) writes are scoped to, from the J2 token. */
    private String accountNode(Token token) {
        String accountId = token == null ? null : token.accountId();
        if (accountId == null || accountId.isBlank())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "accountId");
        return accountId.startsWith("act_") ? accountId : "act_" + accountId;
    }
}
