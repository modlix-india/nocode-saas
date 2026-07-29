package com.modlix.saas.adzump.platform.google;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.google.ads.googleads.v24.services.GenerateKeywordIdeaResult;
import com.google.ads.googleads.v24.services.GeoTargetConstantSuggestion;
import com.google.ads.googleads.v24.services.GoogleAdsRow;
import com.modlix.saas.adzump.compile.GoogleCompiler;
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
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;

/**
 * The Google {@link AdPlatform} implementation (J4). Registering this as a {@code @Component} is all
 * it takes for {@code AdPlatformRegistry} to resolve {@link Platform#GOOGLE}; the SPI stays free of
 * any {@code google-ads} type. All real I/O goes through {@link GoogleAdsClientFacade}; the
 * campaign create/mutate lives in {@link GoogleLifecycle} and the GAQL reads in
 * {@link GoogleGaqlReader}. {@link #compiler()} returns the injected J7 {@link GoogleCompiler}.
 *
 * <p>Capabilities are data-driven per the seam design: transparent Search carries the full surgical
 * lever set at the AD grain; opaque Performance Max a thin set at the CAMPAIGN grain; the semi types
 * sit in between. Google has no page/pixel concept, so {@link #pages}/{@link #pixels} are empty
 * (capability-gated) while RSA + keywords are yes and lead forms are no (the landing page carries the
 * form, A6).
 */
@Component
public class GooglePlatform implements AdPlatform {

    private static final String INTEREST_AUDIENCE_GAQL = "SELECT audience.id, audience.name FROM audience";

    private final GoogleAdsClientFacade facade;
    private final GoogleCompiler compiler;
    private final GoogleLifecycle lifecycle;
    private final GoogleGaqlReader gaqlReader;
    private final AdzumpMessageResourceService msg;

    public GooglePlatform(GoogleAdsClientFacade facade, GoogleCompiler compiler, GoogleLifecycle lifecycle,
            GoogleGaqlReader gaqlReader, AdzumpMessageResourceService msg) {
        this.facade = facade;
        this.compiler = compiler;
        this.lifecycle = lifecycle;
        this.gaqlReader = gaqlReader;
        this.msg = msg;
    }

    @Override
    public Platform code() {
        return Platform.GOOGLE;
    }

    @Override
    public PlatformCapabilities capabilities() {
        Map<CampaignType, OptimizationProfile> profiles = new EnumMap<>(CampaignType.class);

        // Transparent Search: full surgical control at the ad grain (RSA yes, keywords yes, lead-forms no).
        profiles.put(CampaignType.SEARCH, new OptimizationProfile(Set.of(
                Lever.KEYWORD, Lever.NEGATIVE_KEYWORD, Lever.PLACEMENT, Lever.AUDIENCE,
                Lever.BID, Lever.BUDGET, Lever.CREATIVE_ROTATE), Grain.AD));
        // Dynamic Search Ads: Google auto-targets from the site, so no positive keyword lever.
        profiles.put(CampaignType.DSA, new OptimizationProfile(Set.of(
                Lever.NEGATIVE_KEYWORD, Lever.PLACEMENT, Lever.AUDIENCE, Lever.BID, Lever.BUDGET,
                Lever.CREATIVE_ROTATE), Grain.AD));
        // Semi-transparent audience/placement types.
        OptimizationProfile semi = new OptimizationProfile(Set.of(
                Lever.AUDIENCE, Lever.PLACEMENT, Lever.CREATIVE_ROTATE, Lever.BID, Lever.BUDGET), Grain.AD);
        profiles.put(CampaignType.DISPLAY, semi);
        profiles.put(CampaignType.DEMAND_GEN, semi);
        profiles.put(CampaignType.VIDEO, semi);
        profiles.put(CampaignType.APP, new OptimizationProfile(Set.of(
                Lever.BUDGET, Lever.BID, Lever.GOAL, Lever.CREATIVE_ROTATE), Grain.AD));
        profiles.put(CampaignType.SHOPPING, new OptimizationProfile(Set.of(
                Lever.BUDGET, Lever.BID, Lever.LISTING_GROUP), Grain.AD));
        // Opaque Performance Max: thin profile at the campaign grain.
        profiles.put(CampaignType.PMAX, new OptimizationProfile(Set.of(
                Lever.BUDGET, Lever.GOAL, Lever.AUDIENCE_SIGNAL, Lever.ASSET_GROUP, Lever.LISTING_GROUP),
                Grain.CAMPAIGN));

        return new PlatformCapabilities(profiles);
    }

    // --- discovery ------------------------------------------------------------------------------

    @Override
    public List<AdAccountRef> accounts(Token t) {
        List<AdAccountRef> accounts = new ArrayList<>();
        for (String resourceName : this.facade.listAccessibleCustomers(t)) {
            String id = GoogleTokens.idFromResourceName(resourceName);
            // listAccessibleCustomers returns ids only; name/currency enrichment is a follow-up read.
            accounts.add(new AdAccountRef(id, id, null));
        }
        return accounts;
    }

    /** Google has no Facebook-Page concept; capability-gated to empty. */
    @Override
    public List<PageRef> pages(Token t, String accountId) {
        return List.of();
    }

    /** Google conversion sources are not a discrete pixel; capability-gated to empty for P1. */
    @Override
    public List<PixelRef> pixels(Token t, String accountId) {
        return List.of();
    }

    @Override
    public List<GeoRef> searchGeo(Token t, String query) {
        List<GeoRef> geos = new ArrayList<>();
        for (GeoTargetConstantSuggestion suggestion : this.facade.suggestGeoTargets(t, query, null)) {
            var constant = suggestion.getGeoTargetConstant();
            String name = constant.getCanonicalName().isBlank() ? constant.getName() : constant.getCanonicalName();
            geos.add(new GeoRef(Long.toString(constant.getId()), name, constant.getTargetType()));
        }
        return geos;
    }

    /**
     * Google audience segments. P1 reads the account's own audiences via GAQL and filters by name;
     * affinity / in-market constant discovery is a follow-up (kept behind the same seam).
     */
    @Override
    public List<AudienceRef> searchInterests(Token t, String query) {
        String customerId = GoogleTokens.requireCustomerIdString(t, this.msg);
        String needle = query == null ? "" : query.trim().toLowerCase();

        List<AudienceRef> audiences = new ArrayList<>();
        for (GoogleAdsRow row : this.facade.search(t, customerId, INTEREST_AUDIENCE_GAQL)) {
            String name = row.getAudience().getName();
            if (needle.isEmpty() || name.toLowerCase().contains(needle))
                audiences.add(new AudienceRef(Long.toString(row.getAudience().getId()), name, "AUDIENCE"));
        }
        return audiences;
    }

    @Override
    public List<KeywordRef> searchKeywords(Token t, KeywordSeed seed) {
        String customerId = GoogleTokens.requireCustomerIdString(t, this.msg);
        List<String> seeds = seed == null ? List.of() : seed.seeds();
        String url = seed == null ? null : seed.url();

        List<KeywordRef> keywords = new ArrayList<>();
        for (GenerateKeywordIdeaResult idea : this.facade.generateKeywordIdeas(t, customerId, seeds, url))
            keywords.add(new KeywordRef(idea.getText(), idea.getKeywordIdeaMetrics().getAvgMonthlySearches()));
        return keywords;
    }

    // --- compile --------------------------------------------------------------------------------

    @Override
    public PlatformCompiler compiler() {
        return this.compiler;
    }

    // --- lifecycle ------------------------------------------------------------------------------

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

    // --- read -----------------------------------------------------------------------------------

    @Override
    public List<PlatformInsight> insights(Token t, InsightQuery q) {
        return this.gaqlReader.insights(t, q);
    }
}
