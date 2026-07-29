package com.modlix.saas.adzump.platform;

import java.util.List;

import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Money;

/**
 * The seam that keeps the loop platform-neutral. Every ad platform (Meta = J3, Google = J4, future)
 * implements this one interface; J7 (compile), J8 (lifecycle) and J10 (metrics) talk only to the
 * SPI, never to a platform SDK directly. All logic, no CRUD — it is an abstraction, not an entity.
 *
 * <p>Every method takes the connection {@link Token} (resolved by J2 from Core); the SPI never
 * fetches its own credentials. No platform SDK type appears in any signature here. Implementations
 * register themselves as Spring beans and are indexed by {@link AdPlatformRegistry}; adding a
 * platform is adding a bean, nothing else changes.
 */
public interface AdPlatform {

    /** META | GOOGLE. */
    Platform code();

    /** Supported campaign types + per-type optimizable levers + finest reporting grain. */
    PlatformCapabilities capabilities();

    // --- discovery: populate the agent's fetched-id set; ids are platform-real ------------------

    List<AdAccountRef> accounts(Token t);

    /** Meta only (capabilities-gated); Google impls return an empty list. */
    List<PageRef> pages(Token t, String accountId);

    List<PixelRef> pixels(Token t, String accountId);

    List<GeoRef> searchGeo(Token t, String query);

    /** Meta detailed targeting / Google audience segments. */
    List<AudienceRef> searchInterests(Token t, String query);

    /** Google keyword ideas; Meta returns an empty list. */
    List<KeywordRef> searchKeywords(Token t, KeywordSeed seed);

    // --- compile: IR -> platform payload (the pure J7 step, exposed as a sub-interface) ---------

    /** The platform's pure IR-to-payload compiler (J7). Independently testable, no I/O. */
    PlatformCompiler compiler();

    // --- lifecycle: mutations; ids come back for the caller (J8) to write onto plan.links -------

    LaunchResult launchPaused(CompiledCampaign c, Token t);

    /** PAUSE | ACTIVE | ARCHIVED. */
    void setStatus(Token t, PlatformRef ref, RunState s);

    void updateBudget(Token t, PlatformRef ref, Money daily);

    void updateBid(Token t, PlatformRef ref, BidSpec bid);

    void mutateTargeting(Token t, PlatformRef adSet, TargetingPatch p);

    CreativeRef upsertCreative(Token t, PlatformRef adSet, CompiledCreative c);

    // --- read: insights at a grain (the loop's fast signal, J10) --------------------------------

    /** grain = CAMPAIGN | ADSET | AD; the query forces the grain explicit so J10 can't get zeroes. */
    List<PlatformInsight> insights(Token t, InsightQuery q);
}
