package com.modlix.saas.adzump.platform.meta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import com.modlix.saas.adzump.compile.MetaCompiler;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.platform.AdAccountRef;
import com.modlix.saas.adzump.platform.AdPlatform;
import com.modlix.saas.adzump.platform.AudienceRef;
import com.modlix.saas.adzump.platform.BidSpec;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.platform.CompiledCreative;
import com.modlix.saas.adzump.platform.CreativeRef;
import com.modlix.saas.adzump.platform.GeoRef;
import com.modlix.saas.adzump.platform.InsightQuery;
import com.modlix.saas.adzump.platform.KeywordRef;
import com.modlix.saas.adzump.platform.KeywordSeed;
import com.modlix.saas.adzump.platform.LaunchResult;
import com.modlix.saas.adzump.platform.Lever;
import com.modlix.saas.adzump.platform.OptimizationProfile;
import com.modlix.saas.adzump.platform.PageRef;
import com.modlix.saas.adzump.platform.PixelRef;
import com.modlix.saas.adzump.platform.PlatformCapabilities;
import com.modlix.saas.adzump.platform.PlatformCompiler;
import com.modlix.saas.adzump.platform.PlatformInsight;
import com.modlix.saas.adzump.platform.PlatformRef;
import com.modlix.saas.adzump.platform.RunState;
import com.modlix.saas.adzump.platform.TargetingPatch;
import com.modlix.saas.adzump.platform.Token;

/**
 * J3 — the Meta {@link AdPlatform}. A {@code @Component} whose {@link #code()} is {@link Platform#META},
 * so {@code AdPlatformRegistry} indexes it automatically; adding this bean is all it takes for J7/J8/
 * J10 to reach Meta through the SPI. It owns no I/O logic itself: discovery maps raw Graph reads
 * ({@link MetaGraphClient}) into platform-real refs, {@link #compiler()} returns the injected J7
 * {@link MetaCompiler}, lifecycle delegates to {@link MetaLifecycle}, and reads delegate to
 * {@link MetaInsightsReader}.
 *
 * <p>The test double {@code NoopPlatform} stays in test scope and is never a bean, so there is no
 * registry conflict with this real impl.</p>
 */
@Component
public class MetaPlatform implements AdPlatform {

    private final MetaGraphClient graph;
    private final MetaCompiler compiler;
    private final MetaLifecycle lifecycle;
    private final MetaInsightsReader insightsReader;

    public MetaPlatform(MetaGraphClient graph, MetaCompiler compiler, MetaLifecycle lifecycle,
            MetaInsightsReader insightsReader) {
        this.graph = graph;
        this.compiler = compiler;
        this.lifecycle = lifecycle;
        this.insightsReader = insightsReader;
    }

    @Override
    public Platform code() {
        return Platform.META;
    }

    /**
     * Meta capabilities, data-driven per campaign type (replaces the old "lead forms? RSA?" flags).
     * Native lead forms and video creatives are supported (they surface as the LEADS type + the
     * per-type creative capability); Meta has no keyword surface, so no keyword levers are exposed and
     * {@link #searchKeywords} is empty. Advantage+ is the opaque/automated type: a thin lever set at
     * the campaign grain.
     */
    @Override
    public PlatformCapabilities capabilities() {

        Map<CampaignType, OptimizationProfile> profiles = new EnumMap<>(CampaignType.class);

        // Transparent objectives: audience/placement/creative/bid/budget, reporting at the ad grain.
        Set<Lever> transparent = Set.of(Lever.AUDIENCE, Lever.PLACEMENT, Lever.CREATIVE_ROTATE, Lever.BID,
                Lever.BUDGET);
        profiles.put(CampaignType.LEADS, new OptimizationProfile(transparent, Grain.AD));
        profiles.put(CampaignType.SALES, new OptimizationProfile(transparent, Grain.AD));
        profiles.put(CampaignType.TRAFFIC, new OptimizationProfile(transparent, Grain.AD));
        profiles.put(CampaignType.APP, new OptimizationProfile(transparent, Grain.AD));

        // Awareness/Engagement optimize reach/engagement, not conversions — no bid-target lever.
        Set<Lever> reach = Set.of(Lever.AUDIENCE, Lever.PLACEMENT, Lever.CREATIVE_ROTATE, Lever.BUDGET);
        profiles.put(CampaignType.AWARENESS, new OptimizationProfile(reach, Grain.AD));
        profiles.put(CampaignType.ENGAGEMENT, new OptimizationProfile(reach, Grain.AD));

        // Advantage+ (Meta's PMax analog): only what Meta exposes, reporting at the campaign grain.
        profiles.put(CampaignType.ADVANTAGE_PLUS, new OptimizationProfile(
                Set.of(Lever.BUDGET, Lever.GOAL, Lever.AUDIENCE_SIGNAL, Lever.CREATIVE_ROTATE), Grain.CAMPAIGN));

        return new PlatformCapabilities(profiles);
    }

    // --- discovery: raw Graph reads → platform-real refs (the agent's fetched-id set) ------------

    @Override
    public List<AdAccountRef> accounts(Token token) {
        List<AdAccountRef> accounts = new ArrayList<>();
        for (JsonNode business : dataOf(this.graph.get(token, "me/businesses", Map.of("fields", "id,name")))) {
            String businessId = text(business, "id");
            if (businessId == null)
                continue;
            collectAccounts(token, businessId, "owned_ad_accounts", accounts);
            collectAccounts(token, businessId, "client_ad_accounts", accounts);
        }
        return accounts;
    }

    private void collectAccounts(Token token, String businessId, String edge, List<AdAccountRef> into) {
        JsonNode resp = this.graph.get(token, businessId + "/" + edge,
                Map.of("fields", "id,account_id,name,currency"));
        for (JsonNode node : dataOf(resp)) {
            String id = text(node, "id");
            if (id == null) {
                String accountId = text(node, "account_id");
                id = accountId == null ? null : "act_" + accountId;
            }
            if (id != null)
                into.add(new AdAccountRef(id, text(node, "name"), text(node, "currency")));
        }
    }

    /**
     * Meta business pages (capabilities-gated; Google returns empty). Pages are owned by the business,
     * not the ad account, so {@code accountId} is not part of the query — it is accepted for SPI
     * uniformity.
     */
    @Override
    public List<PageRef> pages(Token token, String accountId) {
        List<PageRef> pages = new ArrayList<>();
        for (JsonNode business : dataOf(this.graph.get(token, "me/businesses", Map.of("fields", "id,name")))) {
            String businessId = text(business, "id");
            if (businessId == null)
                continue;
            JsonNode resp = this.graph.get(token, businessId + "/owned_pages", Map.of("fields", "id,name"));
            for (JsonNode node : dataOf(resp)) {
                String id = text(node, "id");
                if (id != null)
                    pages.add(new PageRef(id, text(node, "name")));
            }
        }
        return pages;
    }

    @Override
    public List<PixelRef> pixels(Token token, String accountId) {
        List<PixelRef> pixels = new ArrayList<>();
        JsonNode resp = this.graph.get(token, accountNode(accountId) + "/adspixels", Map.of("fields", "id,name"));
        for (JsonNode node : dataOf(resp)) {
            String id = text(node, "id");
            if (id != null)
                pixels.add(new PixelRef(id, text(node, "name")));
        }
        return pixels;
    }

    @Override
    public List<GeoRef> searchGeo(Token token, String query) {
        List<GeoRef> geos = new ArrayList<>();
        JsonNode resp = this.graph.get(token, "search",
                Map.of("type", "adgeolocation", "q", query == null ? "" : query));
        for (JsonNode node : dataOf(resp)) {
            String key = text(node, "key");
            if (key != null)
                geos.add(new GeoRef(key, text(node, "name"), metaGeoType(text(node, "type"))));
        }
        return geos;
    }

    @Override
    public List<AudienceRef> searchInterests(Token token, String query) {
        List<AudienceRef> interests = new ArrayList<>();
        JsonNode resp = this.graph.get(token, "search",
                Map.of("type", "adinterest", "q", query == null ? "" : query));
        for (JsonNode node : dataOf(resp)) {
            String id = text(node, "id");
            if (id != null)
                interests.add(new AudienceRef(id, text(node, "name"), "INTEREST"));
        }
        return interests;
    }

    /** Meta has no keyword surface (capability-gated) — always empty; no Graph call is made. */
    @Override
    public List<KeywordRef> searchKeywords(Token token, KeywordSeed seed) {
        return List.of();
    }

    // --- compile / lifecycle / read: delegate to the J7 compiler + the Meta sequencer/reader -----

    @Override
    public PlatformCompiler compiler() {
        return this.compiler;
    }

    @Override
    public LaunchResult launchPaused(CompiledCampaign c, Token t) {
        return this.lifecycle.launchPaused(c, t);
    }

    @Override
    public void setStatus(Token t, PlatformRef ref, RunState s) {
        this.lifecycle.setStatus(t, ref, s);
    }

    @Override
    public void updateBudget(Token t, PlatformRef ref, Money daily) {
        this.lifecycle.updateBudget(t, ref, daily);
    }

    @Override
    public void updateBid(Token t, PlatformRef ref, BidSpec bid) {
        this.lifecycle.updateBid(t, ref, bid);
    }

    @Override
    public void mutateTargeting(Token t, PlatformRef adSet, TargetingPatch p) {
        this.lifecycle.mutateTargeting(t, adSet, p);
    }

    @Override
    public CreativeRef upsertCreative(Token t, PlatformRef adSet, CompiledCreative c) {
        return this.lifecycle.upsertCreative(t, adSet, c);
    }

    @Override
    public List<PlatformInsight> insights(Token t, InsightQuery q) {
        return this.insightsReader.insights(t, q);
    }

    // --- helpers --------------------------------------------------------------------------------

    private static List<JsonNode> dataOf(JsonNode response) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode data = response == null ? null : response.get("data");
        if (data != null && data.isArray())
            data.forEach(out::add);
        return out;
    }

    private static String text(JsonNode node, String field) {
        if (node == null)
            return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String accountNode(String accountId) {
        if (accountId == null || accountId.isBlank())
            return "act_";
        return accountId.startsWith("act_") ? accountId : "act_" + accountId;
    }

    /** Meta geo types → the neutral GeoRef.type vocabulary. */
    private static String metaGeoType(String metaType) {
        if (metaType == null)
            return null;
        return switch (metaType.trim().toLowerCase()) {
            case "country" -> "COUNTRY";
            case "region" -> "REGION";
            case "city" -> "CITY";
            case "zip" -> "POSTAL_CODE";
            default -> metaType.trim().toUpperCase();
        };
    }
}
