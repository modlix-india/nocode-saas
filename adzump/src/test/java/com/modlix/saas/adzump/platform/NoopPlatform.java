package com.modlix.saas.adzump.platform;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.modlix.saas.adzump.compile.EffectiveConfig;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.CreativeFormat;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.Objective;
import com.modlix.saas.adzump.model.leadzump.Grain;

/**
 * An in-memory {@link AdPlatform} test double so J7/J8/J10 and the eval harness can be built and
 * tested before J3/J4 hit real APIs — no network, no SDK. It records every call (see the getters)
 * for assertions, returns canned non-empty discovery refs and insights, and its
 * {@link #compiler()} echoes a {@link CompiledCampaign}. {@link #launchPaused} returns a success
 * carrying fake platform ids, or — when the double is constructed in fail-mode — a
 * {@link LaunchResult#failed} so J8 partial-failure paths can be exercised.
 *
 * <p>Lives in {@code src/test}: it is never a Spring bean, so it does not pollute the real registry.
 */
public class NoopPlatform implements AdPlatform {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Platform code;
    private final boolean failMode;
    private final PlatformCompiler compiler;

    // --- recorded calls (exposed via getters for assertions) ------------------------------------
    private final List<String> callLog = new ArrayList<>();
    private final List<CampaignPlan> compiledPlans = new ArrayList<>();
    private final List<CompiledCampaign> launchedCampaigns = new ArrayList<>();
    private final List<StatusCall> statusCalls = new ArrayList<>();
    private final List<BudgetCall> budgetCalls = new ArrayList<>();
    private final List<BidCall> bidCalls = new ArrayList<>();
    private final List<TargetingCall> targetingCalls = new ArrayList<>();
    private final List<CreativeCall> creativeCalls = new ArrayList<>();
    private final List<InsightQuery> insightQueries = new ArrayList<>();

    public record StatusCall(PlatformRef ref, RunState state) {
    }

    public record BudgetCall(PlatformRef ref, Money daily) {
    }

    public record BidCall(PlatformRef ref, BidSpec bid) {
    }

    public record TargetingCall(PlatformRef ref, TargetingPatch patch) {
    }

    public record CreativeCall(PlatformRef ref, CompiledCreative creative) {
    }

    /** Success-mode Meta double. */
    public NoopPlatform() {
        this(Platform.META, false);
    }

    public NoopPlatform(Platform code, boolean failMode) {
        this.code = code;
        this.failMode = failMode;
        this.compiler = new NoopCompiler();
    }

    @Override
    public Platform code() {
        return this.code;
    }

    @Override
    public PlatformCapabilities capabilities() {
        this.callLog.add("capabilities");

        Map<CampaignType, OptimizationProfile> profiles = new EnumMap<>(CampaignType.class);

        if (this.code == Platform.GOOGLE) {
            // Transparent Search: full surgical levers at the AD grain.
            profiles.put(CampaignType.SEARCH, new OptimizationProfile(java.util.Set.of(
                    Lever.KEYWORD, Lever.NEGATIVE_KEYWORD, Lever.PLACEMENT, Lever.AUDIENCE,
                    Lever.BID, Lever.BUDGET, Lever.CREATIVE_ROTATE), Grain.AD));
            // Opaque PMax: thin profile at the campaign grain.
            profiles.put(CampaignType.PMAX, new OptimizationProfile(java.util.Set.of(
                    Lever.BUDGET, Lever.GOAL, Lever.AUDIENCE_SIGNAL, Lever.ASSET_GROUP, Lever.LISTING_GROUP),
                    Grain.CAMPAIGN));
        } else {
            // Meta LEADS: audience/placement/creative/bid/budget at the ad grain.
            profiles.put(CampaignType.LEADS, new OptimizationProfile(java.util.Set.of(
                    Lever.AUDIENCE, Lever.PLACEMENT, Lever.CREATIVE_ROTATE, Lever.BID, Lever.BUDGET),
                    Grain.AD));
            // Advantage+: thin profile at the campaign grain.
            profiles.put(CampaignType.ADVANTAGE_PLUS, new OptimizationProfile(java.util.Set.of(
                    Lever.BUDGET, Lever.GOAL, Lever.AUDIENCE_SIGNAL, Lever.CREATIVE_ROTATE), Grain.CAMPAIGN));
        }

        return new PlatformCapabilities(profiles);
    }

    // --- discovery ------------------------------------------------------------------------------

    @Override
    public List<AdAccountRef> accounts(Token t) {
        this.callLog.add("accounts");
        return List.of(new AdAccountRef("act_noop_1", "Noop Account", "USD"));
    }

    @Override
    public List<PageRef> pages(Token t, String accountId) {
        this.callLog.add("pages");
        // Meta only (capabilities-gated); Google returns empty.
        return this.code == Platform.META ? List.of(new PageRef("page_noop_1", "Noop Page")) : List.of();
    }

    @Override
    public List<PixelRef> pixels(Token t, String accountId) {
        this.callLog.add("pixels");
        return List.of(new PixelRef("pixel_noop_1", "Noop Pixel"));
    }

    @Override
    public List<GeoRef> searchGeo(Token t, String query) {
        this.callLog.add("searchGeo");
        return List.of(new GeoRef("geo_noop_1", "Noopville", "CITY"));
    }

    @Override
    public List<AudienceRef> searchInterests(Token t, String query) {
        this.callLog.add("searchInterests");
        return List.of(new AudienceRef("aud_noop_1", "Noop Interest", "INTEREST"));
    }

    @Override
    public List<KeywordRef> searchKeywords(Token t, KeywordSeed seed) {
        this.callLog.add("searchKeywords");
        // Google only; Meta returns empty.
        return this.code == Platform.GOOGLE ? List.of(new KeywordRef("noop keyword", 1000L)) : List.of();
    }

    // --- compile --------------------------------------------------------------------------------

    @Override
    public PlatformCompiler compiler() {
        return this.compiler;
    }

    // --- lifecycle ------------------------------------------------------------------------------

    @Override
    public LaunchResult launchPaused(CompiledCampaign c, Token t) {
        this.callLog.add("launchPaused");
        this.launchedCampaigns.add(c);

        if (this.failMode)
            return LaunchResult.failed(this.code, "noop-forced-failure");

        String accountId = t == null ? null : t.accountId();

        if (this.code == Platform.GOOGLE) {
            Links links = new Links().setGoogle(new Links.GoogleLinks()
                    .setAdAccountId(accountId)
                    .setCampaignId("noop-google-campaign-1"));
            return LaunchResult.ok(this.code, links);
        }

        Links links = new Links().setMeta(new Links.MetaLinks()
                .setAdAccountId(accountId)
                .setPageId("page_noop_1")
                .setPixelId("pixel_noop_1")
                .setCampaignId("noop-meta-campaign-1"));
        return LaunchResult.ok(this.code, links);
    }

    @Override
    public void setStatus(Token t, PlatformRef ref, RunState s) {
        this.callLog.add("setStatus");
        this.statusCalls.add(new StatusCall(ref, s));
    }

    @Override
    public void updateBudget(Token t, PlatformRef ref, Money daily) {
        this.callLog.add("updateBudget");
        this.budgetCalls.add(new BudgetCall(ref, daily));
    }

    @Override
    public void updateBid(Token t, PlatformRef ref, BidSpec bid) {
        this.callLog.add("updateBid");
        this.bidCalls.add(new BidCall(ref, bid));
    }

    @Override
    public void mutateTargeting(Token t, PlatformRef adSet, TargetingPatch p) {
        this.callLog.add("mutateTargeting");
        this.targetingCalls.add(new TargetingCall(adSet, p));
    }

    @Override
    public CreativeRef upsertCreative(Token t, PlatformRef adSet, CompiledCreative c) {
        this.callLog.add("upsertCreative");
        this.creativeCalls.add(new CreativeCall(adSet, c));
        return new CreativeRef("creative_noop_1");
    }

    // --- read -----------------------------------------------------------------------------------

    @Override
    public List<PlatformInsight> insights(Token t, InsightQuery q) {
        this.callLog.add("insights");
        this.insightQueries.add(q);

        Grain grain = q == null ? Grain.CAMPAIGN : q.grain();
        List<String> ids = (q == null || q.ids().isEmpty()) ? List.of("noop-entity-1") : q.ids();

        List<PlatformInsight> rows = new ArrayList<>();
        for (String id : ids)
            rows.add(new PlatformInsight(grain, new PlatformRef(entityType(grain), id),
                    1000L, 50L, new Money().setAmount(new BigDecimal("12.34")).setCurrency("USD"), 5L));

        return rows;
    }

    private static String entityType(Grain grain) {
        return switch (grain) {
            case CAMPAIGN -> "campaign";
            case ADSET -> "adSet";
            case AD -> "ad";
        };
    }

    // --- recorded-call getters ------------------------------------------------------------------

    public List<String> getCallLog() {
        return this.callLog;
    }

    public List<CampaignPlan> getCompiledPlans() {
        return this.compiledPlans;
    }

    public List<CompiledCampaign> getLaunchedCampaigns() {
        return this.launchedCampaigns;
    }

    public List<StatusCall> getStatusCalls() {
        return this.statusCalls;
    }

    public List<BudgetCall> getBudgetCalls() {
        return this.budgetCalls;
    }

    public List<BidCall> getBidCalls() {
        return this.bidCalls;
    }

    public List<TargetingCall> getTargetingCalls() {
        return this.targetingCalls;
    }

    public List<CreativeCall> getCreativeCalls() {
        return this.creativeCalls;
    }

    public List<InsightQuery> getInsightQueries() {
        return this.insightQueries;
    }

    /** The no-op compiler: pure, echoes the plan into a type-tagged {@link CompiledCampaign}. */
    private final class NoopCompiler implements PlatformCompiler {

        @Override
        public Platform platform() {
            return NoopPlatform.this.code;
        }

        @Override
        public CompiledCampaign compile(CampaignPlan plan, EffectiveConfig cfg) {
            NoopPlatform.this.compiledPlans.add(plan);

            CampaignType type = null;
            if (plan != null && plan.getCampaignTypes() != null) {
                type = plan.getCampaignTypes().get(NoopPlatform.this.code);
                if (type == null && !plan.getCampaignTypes().isEmpty())
                    type = plan.getCampaignTypes().values().iterator().next();
            }

            Objective objective = (plan != null && plan.getBody() != null) ? plan.getBody().getObjective() : null;
            String name = plan == null ? null : plan.getName();

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("echo", true);
            payload.put("platform", NoopPlatform.this.code.getLiteral());
            payload.put("name", name);
            payload.put("type", type == null ? null : type.getLiteral());
            // Marker so callers know a real payload tree (RSA/asset-group/keyword) is a J3/J4 concern.
            payload.putObject("tree").put("compiledBy", "NoopCompiler");

            return new CompiledCampaign(NoopPlatform.this.code, type, name, objective, payload);
        }
    }

    /** Convenience for tests that want a leading creative format without hand-building one. */
    public static CompiledCreative sampleCreative(CreativeFormat format) {
        return new CompiledCreative(format, MAPPER.createObjectNode().put("noop", true));
    }
}
