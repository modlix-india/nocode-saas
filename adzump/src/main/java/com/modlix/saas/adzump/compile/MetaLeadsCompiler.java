package com.modlix.saas.adzump.compile;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.enums.PlatformObjective;
import com.modlix.saas.adzump.enums.SpecialAdCategory;
import com.modlix.saas.adzump.model.Ad;
import com.modlix.saas.adzump.model.AdGroup;
import com.modlix.saas.adzump.model.Audiences;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Copy;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.Demographics;
import com.modlix.saas.adzump.model.Geo;
import com.modlix.saas.adzump.model.LeadForm;
import com.modlix.saas.adzump.model.LeadFormField;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.ScheduleConfig;
import com.modlix.saas.adzump.model.Targeting;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.vertical.PolicyDefaults;

/**
 * Meta {@code LEADS} (instant-form lead gen) {@link TypeCompiler} — the P1-first, fullest-control
 * Meta path. Builds the campaign → ad set(s) → ad(s) + creative(s) tree plus the native lead
 * form(s), everything PAUSED, as a {@code JsonNode} payload J3 maps onto Graph API create calls.
 *
 * <p>
 * Shapes ported from the legacy {@code Adzump-AI/agents/meta/payload_builders/*} (campaign /
 * ad set / targeting / creative / lead form structure); the <b>hardcoded fallback ids and constants
 * were deliberately dropped</b> — a missing page id / budget / currency is a J6 failure, not a
 * silent default (that deletion is the fix). Every value here traces to the plan or the
 * {@link EffectiveConfig}. Load-bearing for real estate: when compliance resolves to
 * {@link SpecialAdCategory#HOUSING} the campaign carries {@code special_ad_categories=["HOUSING"]}
 * and the ad-set targeting drops age/gender narrowing (the restricted-targeting shape J3 asserts
 * pre-flight).
 * </p>
 */
@Component
public class MetaLeadsCompiler implements TypeCompiler {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    // Structural constants for the on-ad lead-gen path (determined by the campaign TYPE, not business
    // defaults): an instant-form ad optimizes for LEAD_GENERATION, billed on impressions, and Meta
    // requires this placeholder link on lead-gen link_data. None of these is an account/page/form id.
    private static final String DESTINATION_ON_AD = "ON_AD";
    private static final String OPTIMIZATION_GOAL_LEADS = "LEAD_GENERATION";
    private static final String BILLING_EVENT = "IMPRESSIONS";
    private static final String LEAD_GEN_LINK_PLACEHOLDER = "http://fb.me/";

    @Override
    public CompiledCampaign compile(CampaignPlan plan, EffectiveConfig cfg) {

        CampaignPlanBody body = requireBody(plan);
        String pageId = metaPageId(plan);

        ObjectNode root = NODES.objectNode();

        root.set("campaign", buildCampaign(plan, body, cfg));

        // Compliance directive read by J3's pre-flight (MetaLifecycle): the resolved special-ad-category
        // this launch MUST declare. J7 stamps the requirement here and builds the matching
        // special_ad_categories on the campaign above from the same source; J3 asserts the two agree
        // before any Graph call, so a non-compliant RE lead-gen payload is rejected pre-flight rather
        // than bounced by Meta. Not part of any create body — J3 reads only known campaign/adSet fields.
        SpecialAdCategory category = cfg == null ? null : cfg.specialAdCategory();
        if (category != null && category != SpecialAdCategory.NONE)
            root.putObject("compliance").put("requiredSpecialAdCategory", category.getLiteral());

        LeadForm leadForm = body.getLeadForm();
        ArrayNode leadForms = root.putArray("leadForms");
        if (leadForm != null)
            leadForms.add(buildLeadForm(leadForm));

        ArrayNode adSets = root.putArray("adSets");
        for (AdGroup adGroup : metaAdGroups(body))
            adSets.add(buildAdSet(adGroup, body, cfg, pageId));

        return new CompiledCampaign(Platform.META, CampaignType.LEADS, plan.getName(), body.getObjective(), root);
    }

    // --- campaign -----------------------------------------------------------

    private ObjectNode buildCampaign(CampaignPlan plan, CampaignPlanBody body, EffectiveConfig cfg) {

        ObjectNode campaign = NODES.objectNode();
        campaign.put("name", plan.getName());
        campaign.put("objective", metaObjective(cfg.objective()));
        campaign.put("status", "PAUSED");
        campaign.put("buying_type", "AUCTION");
        // single ad set per campaign in this path → nothing to share; keep spend deterministic
        campaign.put("is_adset_budget_sharing_enabled", false);

        if (cfg.specialAdCategory() != null && cfg.specialAdCategory() != SpecialAdCategory.NONE)
            campaign.putArray("special_ad_categories").add(cfg.specialAdCategory().getLiteral());

        // CBO: the budget lives on the campaign; ABO puts it on each ad set instead.
        if (cfg.budgetMode() == PolicyDefaults.BudgetMode.CAMPAIGN)
            putBudget(campaign, body.getBudget() == null ? null : body.getBudget().getDailyBudget(),
                    body.getBudget() == null ? null : body.getBudget().getTotalBudget());

        return campaign;
    }

    private void putBudget(ObjectNode target, Money daily, Money lifetime) {
        if (daily != null)
            target.put("daily_budget", MoneyUnits.toMinorUnits(daily));
        else if (lifetime != null)
            target.put("lifetime_budget", MoneyUnits.toMinorUnits(lifetime));
        else
            throw new IllegalStateException("Campaign budget is required for compilation (J6 gates this)");
    }

    // --- ad set -------------------------------------------------------------

    private ObjectNode buildAdSet(AdGroup adGroup, CampaignPlanBody body, EffectiveConfig cfg, String pageId) {

        ObjectNode adSet = NODES.objectNode();
        adSet.put("name", adGroup.getName());
        adSet.put("status", "PAUSED");
        adSet.put("destination_type", DESTINATION_ON_AD);
        adSet.put("optimization_goal", OPTIMIZATION_GOAL_LEADS);
        adSet.put("billing_event", BILLING_EVENT);

        String bidStrategy = metaBidStrategy(cfg.biddingStrategy());
        adSet.put("bid_strategy", bidStrategy);
        if (requiresBidAmount(bidStrategy)) {
            if (cfg.targetCpa() == null)
                throw new IllegalStateException("bid_amount (targetCpa) required for bid_strategy " + bidStrategy);
            adSet.put("bid_amount", MoneyUnits.toMinorUnits(cfg.targetCpa()));
        }

        adSet.putObject("promoted_object").put("page_id", pageId);

        if (cfg.budgetMode() == PolicyDefaults.BudgetMode.AD_SET)
            putBudget(adSet, adGroup.getBudget(), null);

        ScheduleConfig schedule = body.getSchedule();
        if (schedule != null && schedule.getStartAt() != null)
            adSet.put("start_time", DateTimes.metaDateTime(schedule.getStartAt(), cfg.timezone()));
        if (schedule != null && schedule.getEndAt() != null)
            adSet.put("end_time", DateTimes.metaDateTime(schedule.getEndAt(), cfg.timezone()));

        ArrayNode attributionSpec = metaAttributionSpec(cfg.attributionWindow());
        if (attributionSpec != null)
            adSet.set("attribution_spec", attributionSpec);

        adSet.set("targeting", buildTargeting(adGroup.getTargeting(), cfg.specialAdCategory()));

        ArrayNode ads = adSet.putArray("ads");
        if (adGroup.getAds() != null)
            for (Ad ad : adGroup.getAds())
                ads.add(buildAd(ad, body, pageId));

        return adSet;
    }

    private ObjectNode buildTargeting(Targeting targeting, SpecialAdCategory category) {

        ObjectNode node = NODES.objectNode();
        if (targeting == null)
            return node;

        if (targeting.getGeo() != null)
            node.set("geo_locations", buildGeo(targeting.getGeo()));

        boolean housing = category == SpecialAdCategory.HOUSING;

        Demographics demo = targeting.getDemographics();
        if (!housing && demo != null) {
            // Under HOUSING, age/gender narrowing is locked; the compiler drops it (J3 asserts pre-flight).
            if (demo.getAgeMin() != null)
                node.put("age_min", demo.getAgeMin());
            if (demo.getAgeMax() != null)
                node.put("age_max", demo.getAgeMax());
            ArrayNode genders = metaGenders(demo.getGenders());
            if (genders != null)
                node.set("genders", genders);
        }

        Audiences audiences = targeting.getAudiences();
        if (audiences != null && audiences.getInterests() != null && !audiences.getInterests().isEmpty()) {
            ArrayNode interests = NODES.arrayNode();
            for (String interestId : audiences.getInterests())
                interests.add(NODES.objectNode().put("id", interestId));
            node.putArray("flexible_spec").add(NODES.objectNode().set("interests", interests));
        }

        return node;
    }

    private ObjectNode buildGeo(Geo geo) {

        ObjectNode node = NODES.objectNode();

        if (geo.getCenter() != null && geo.getRadiusKm() != null) {
            ObjectNode custom = NODES.objectNode();
            custom.put("latitude", geo.getCenter().getLat());
            custom.put("longitude", geo.getCenter().getLng());
            custom.put("radius", geo.getRadiusKm());
            custom.put("distance_unit", "kilometer");
            node.putArray("custom_locations").add(custom);
        }

        // Named places carry resolved geo keys (from the platform geo search, RETRIEVAL). The IR
        // string is the key here; J3 validates it against the fetched-id set.
        if (geo.getPlaces() != null && !geo.getPlaces().isEmpty()) {
            ArrayNode cities = NODES.arrayNode();
            for (String place : geo.getPlaces())
                cities.add(NODES.objectNode().put("key", place));
            node.set("cities", cities);
        }

        return node;
    }

    private ArrayNode metaGenders(List<String> genders) {
        if (genders == null || genders.isEmpty())
            return null;
        ArrayNode node = NODES.arrayNode();
        boolean any = false;
        for (String g : genders) {
            switch (g == null ? "" : g.trim().toUpperCase()) {
                case "MALE" -> {
                    node.add(1);
                    any = true;
                }
                case "FEMALE" -> {
                    node.add(2);
                    any = true;
                }
                default -> {
                    // "ALL" / unknown ⇒ no gender narrowing (omit the key entirely)
                }
            }
        }
        return any ? node : null;
    }

    // --- ad + creative ------------------------------------------------------

    private ObjectNode buildAd(Ad ad, CampaignPlanBody body, String pageId) {

        ObjectNode node = NODES.objectNode();
        node.put("name", ad.getName());
        node.put("status", "PAUSED");
        node.set("creative", buildCreative(ad, body, pageId));
        return node;
    }

    private ObjectNode buildCreative(Ad ad, CampaignPlanBody body, String pageId) {

        Creative creative = findCreative(body, ad.getCreativeId());
        Copy copy = creative == null ? null : creative.getCopy();

        ObjectNode node = NODES.objectNode();
        node.put("name", ad.getName());

        ObjectNode objectStorySpec = node.putObject("object_story_spec");
        objectStorySpec.put("page_id", pageId);

        ObjectNode linkData = objectStorySpec.putObject("link_data");
        String primaryText = first(copy == null ? null : copy.getPrimaryTexts());
        String headline = first(copy == null ? null : copy.getHeadlines());
        String description = first(copy == null ? null : copy.getDescriptions());
        if (primaryText != null)
            linkData.put("message", primaryText);
        if (headline != null)
            linkData.put("name", headline);
        if (description != null)
            linkData.put("description", description);

        // On-ad lead form: Meta requires this placeholder link; the CTA carries the lead-gen form id.
        linkData.put("link", LEAD_GEN_LINK_PLACEHOLDER);

        String leadFormId = ad.getLeadFormId() != null ? ad.getLeadFormId()
                : (body.getLeadForm() == null ? null : body.getLeadForm().getId());
        if (leadFormId == null)
            throw new IllegalStateException("Meta lead-gen ad requires a lead form id (J6 gates this)");

        String cta = ad.getCallToAction() != null ? ad.getCallToAction() : (copy == null ? null : copy.getCta());
        if (cta == null || cta.isBlank())
            throw new IllegalStateException("Meta lead-gen ad requires a call-to-action (J6 gates this)");

        ObjectNode callToAction = linkData.putObject("call_to_action");
        callToAction.put("type", cta);
        callToAction.putObject("value").put("lead_gen_form_id", leadFormId);

        return node;
    }

    // --- lead form ----------------------------------------------------------

    private ObjectNode buildLeadForm(LeadForm leadForm) {

        ObjectNode node = NODES.objectNode();
        node.put("name", leadForm.getId());

        ArrayNode questions = node.putArray("questions");
        if (leadForm.getFields() != null)
            for (LeadFormField field : leadForm.getFields()) {
                ObjectNode question = questions.addObject();
                if (field.getType() != null)
                    question.put("type", field.getType());
                if (field.getKey() != null)
                    question.put("key", field.getKey());
                if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                    ArrayNode options = question.putArray("options");
                    for (String option : field.getOptions())
                        options.add(NODES.objectNode().put("value", option));
                }
            }

        if (leadForm.getPrivacyPolicyUrl() != null)
            node.putObject("privacy_policy").put("url", leadForm.getPrivacyPolicyUrl());

        if (leadForm.getThankYouMessage() != null)
            node.putObject("thank_you_page").put("body", leadForm.getThankYouMessage());

        return node;
    }

    // --- neutral → Meta enum mapping ---------------------------------------

    private static String metaObjective(PlatformObjective objective) {
        if (objective == null)
            throw new IllegalStateException("Objective is required for compilation");
        return switch (objective) {
            case LEADS -> "OUTCOME_LEADS";
            case SALES, CONVERSIONS -> "OUTCOME_SALES";
            case TRAFFIC -> "OUTCOME_TRAFFIC";
            case AWARENESS -> "OUTCOME_AWARENESS";
            case ENGAGEMENT -> "OUTCOME_ENGAGEMENT";
            case APP -> "OUTCOME_APP_PROMOTION";
        };
    }

    private static String metaBidStrategy(String neutral) {
        if (neutral == null || neutral.isBlank())
            throw new IllegalStateException("Bidding strategy is required for compilation");
        return switch (neutral.trim().toUpperCase()) {
            case "MAXIMIZE_CONVERSIONS", "MAXIMIZE_REACH", "LOWEST_COST", "AUTOMATIC", "LOWEST_COST_WITHOUT_CAP" ->
                "LOWEST_COST_WITHOUT_CAP";
            case "TARGET_CPA", "COST_CAP" -> "COST_CAP";
            case "BID_CAP", "LOWEST_COST_WITH_BID_CAP" -> "LOWEST_COST_WITH_BID_CAP";
            case "MIN_ROAS", "TARGET_ROAS", "LOWEST_COST_WITH_MIN_ROAS" -> "LOWEST_COST_WITH_MIN_ROAS";
            default -> throw new IllegalStateException("Unsupported Meta bidding strategy: " + neutral);
        };
    }

    private static boolean requiresBidAmount(String metaBidStrategy) {
        return "COST_CAP".equals(metaBidStrategy) || "LOWEST_COST_WITH_BID_CAP".equals(metaBidStrategy);
    }

    /**
     * Parses a neutral attribution token ({@code 7d_click_1d_view}, {@code 30d_click}) into Meta's
     * {@code attribution_spec}. Returns {@code null} when no token is set; throws on a malformed token
     * (fail-fast, never a silent default).
     */
    private static ArrayNode metaAttributionSpec(String token) {
        if (token == null || token.isBlank())
            return null;

        ArrayNode spec = NODES.arrayNode();
        String[] parts = token.trim().toLowerCase().split("_");
        for (int i = 0; i + 1 < parts.length; i++) {
            String eventType = switch (parts[i + 1]) {
                case "click" -> "CLICK_THROUGH";
                case "view" -> "VIEW_THROUGH";
                default -> null;
            };
            if (eventType == null)
                continue;
            if (!parts[i].endsWith("d"))
                throw new IllegalStateException("Malformed attribution window token: " + token);
            int days = Integer.parseInt(parts[i].substring(0, parts[i].length() - 1));
            spec.add(NODES.objectNode().put("event_type", eventType).put("window_days", days));
        }

        if (spec.isEmpty())
            throw new IllegalStateException("Malformed attribution window token: " + token);
        return spec;
    }

    // --- plan helpers -------------------------------------------------------

    private static CampaignPlanBody requireBody(CampaignPlan plan) {
        if (plan == null || plan.getBody() == null)
            throw new IllegalStateException("CampaignPlan body is required for compilation");
        return plan.getBody();
    }

    private static String metaPageId(CampaignPlan plan) {
        Links links = plan.getBody().getLinks();
        String pageId = links == null || links.getMeta() == null ? null : links.getMeta().getPageId();
        if (pageId == null || pageId.isBlank())
            throw new IllegalStateException(
                    "Meta pageId missing on plan.links.meta — required to compile a Meta campaign (J6 gates this; "
                            + "no hardcoded fallback)");
        return pageId;
    }

    private static List<AdGroup> metaAdGroups(CampaignPlanBody body) {
        if (body.getAdGroups() == null)
            return List.of();
        return body.getAdGroups().stream().filter(ag -> ag != null && ag.getPlatform() == Platform.META).toList();
    }

    private static Creative findCreative(CampaignPlanBody body, String creativeId) {
        if (creativeId == null || body.getCreatives() == null)
            return null;
        for (Creative creative : body.getCreatives())
            if (creative != null && creativeId.equals(creative.getId()))
                return creative;
        return null;
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }
}
