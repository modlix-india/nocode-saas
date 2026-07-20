package com.modlix.saas.adzump.service.competition;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dao.CompetitionResearchDao;
import com.modlix.saas.adzump.dto.CompetitionResearchEntity;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.competition.AdLibraryQuery;
import com.modlix.saas.adzump.model.competition.Competitor;
import com.modlix.saas.adzump.model.competition.CompetitionResearchBody;
import com.modlix.saas.adzump.model.competition.CompetitionResearchRequest;
import com.modlix.saas.adzump.model.competition.CompetitorAd;
import com.modlix.saas.adzump.model.competition.RankedCompetitorAd;
import com.modlix.saas.adzump.model.competition.RankingBasis;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.connection.ConnectionService;
import com.modlix.saas.adzump.vertical.ProxyWeights;
import com.modlix.saas.adzump.vertical.VerticalRegistry;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J19 — competition research. Mines the Meta Ad Library for a product's competitors/market, ranks the
 * running ads by the {@link BestWorkingProxy} (a belief-revealed proxy, <b>not</b> performance), and
 * persists the proxy-ranked shortlist as tenant-private knowledge. Attribute extraction from the
 * creatives is A4/vision and lands later (J16-stored assets); here we store the ranked ads + metadata so
 * A4 can ground generation and J20 can seed its taxonomy.
 *
 * <p>It is <b>directional discovery, not measurement</b>: leadzump outcomes (J10/J20) remain the only
 * ground truth for what actually converts. Every result is labeled {@link RankingBasis#PROXY} and
 * carries the honest {@link #CAVEATS}.
 *
 * <p><b>Security &amp; tenancy.</b> {@link #research} mutates and carries {@code EDIT}; {@link #get}
 * carries no {@code @PreAuthorize} and is tenant-scoped at runtime. Both resolve an effective client
 * (own by default; a differing {@code targetClientCode} only for the system client or a managing client),
 * mirroring {@code AutonomyConfigService}/{@code CreativeScoringService}, so findings stay
 * <b>tenant-private</b> — one account's competition research never leaks to another (J19 §5.5).
 */
@Service
public class CompetitionResearchService {

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";

    private static final String CLIENT = "client";
    private static final String PRODUCT_ID = "productId";

    /** Honest caveats surfaced with every result (J19 §5.4) — stated, never buried. */
    static final List<String> CAVEATS = List.of(
            "Proxy-ranked, not performance-ranked: the Ad Library exposes no spend, impressions, CTR or "
                    + "conversions for commercial ads, so 'best-working' is belief-revealed, not outcome-proven.",
            "Longevity can mislead: a long-running ad may be set-and-forget that nobody optimized, not a "
                    + "proven winner.",
            "Reach/impressions exist only for political/issue ads in most regions; commercial ranking uses "
                    + "no reach.",
            "Ad Library coverage and available fields vary by region and category.",
            "Use these as a hypothesis source for generation only; leadzump outcomes (J10/J20) remain the "
                    + "only ground truth for what converts.");

    private final AdLibraryClient adLibraryClient;
    private final BestWorkingProxy proxy;
    private final CompetitionResearchDao dao;
    private final ConnectionService connectionService;
    private final VerticalRegistry verticalRegistry;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public CompetitionResearchService(AdLibraryClient adLibraryClient, BestWorkingProxy proxy,
            CompetitionResearchDao dao, ConnectionService connectionService, VerticalRegistry verticalRegistry,
            FeignAuthenticationService securityService, AdzumpMessageResourceService msgService) {

        this.adLibraryClient = adLibraryClient;
        this.proxy = proxy;
        this.dao = dao;
        this.connectionService = connectionService;
        this.verticalRegistry = verticalRegistry;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    /**
     * Runs competition research for a product: fetches the given competitors' (and any keyword-seeded)
     * running ads via the Ad Library, ranks them by the vertical-weighted proxy, and appends a
     * tenant-private finding. The competitor list is an input (A2 discovers it; mocked/param here).
     * Mutating &rarr; {@code EDIT}. Client scope is pinned to the resolved effective client, never trusted
     * from the request.
     */
    @PreAuthorize(EDIT)
    public CompetitionResearchEntity research(String productId, CompetitionResearchRequest request,
            String targetClientCode) {

        if (productId == null || productId.isBlank())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, PRODUCT_ID);

        CompetitionResearchRequest req = request == null ? new CompetitionResearchRequest() : request;

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca);

        String vertical = req.getVertical();
        ProxyWeights weights = this.verticalRegistry.getOrDefault(vertical)
                .competitionProxyWeights()
                .orElseGet(ProxyWeights::defaults);

        List<Competitor> competitors = req.getCompetitors() == null ? List.of() : req.getCompetitors();
        List<String> keywords = req.getKeywords() == null ? List.of() : req.getKeywords();

        List<CompetitorAd> fetched = this.fetchAds(competitors, keywords, req.getReachedCountries());
        List<RankedCompetitorAd> ranked = this.proxy.rank(fetched, vertical, LocalDate.now(), weights);

        LocalDateTime now = LocalDateTime.now();
        CompetitionResearchBody body = new CompetitionResearchBody()
                .setProductId(productId)
                .setVertical(vertical)
                .setGeneratedAt(now)
                .setRankingBasis(RankingBasis.PROXY)
                .setCompetitorPageIds(distinctPageIds(competitors))
                .setWeights(weights)
                .setRankedAds(ranked)
                .setCaveats(CAVEATS);

        CompetitionResearchEntity entity = new CompetitionResearchEntity()
                .setClientCode(clientCode)
                .setProductId(productId)
                .setVertical(vertical)
                .setGeneratedAt(now);
        entity.setBody(body);

        if (ca.getUser() != null)
            entity.setCreatedBy(ULong.valueOf(ca.getUser().getId()));

        return this.dao.create(entity);
    }

    /** The latest research finding for a product, scoped to the caller's own client (J19 §6). */
    public CompetitionResearchEntity get(String productId) {
        return this.get(productId, null);
    }

    /**
     * The latest research finding for a product, or {@code null} when none exists. No
     * {@code @PreAuthorize}: tenant-scoped to the resolved effective client (own by default; a managed
     * cross-client target is allowed), so the finding read is always tenant-private.
     */
    public CompetitionResearchEntity get(String productId, String targetClientCode) {

        if (productId == null || productId.isBlank())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, PRODUCT_ID);

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca);

        return this.dao.findLatestByProduct(clientCode, productId);
    }

    // ==============================================================================================

    /**
     * Pulls running ads for the competitors and keyword seeds. Resolves the Meta token lazily via J2 only
     * when there is something to fetch (no competitors + no keywords => no live call, empty result).
     */
    private List<CompetitorAd> fetchAds(List<Competitor> competitors, List<String> keywords,
            List<String> reachedCountries) {

        List<CompetitorAd> out = new ArrayList<>();
        boolean hasWork = competitors.stream().anyMatch(c -> c != null && c.getPageId() != null && !c.getPageId().isBlank())
                || keywords.stream().anyMatch(k -> k != null && !k.isBlank());
        if (!hasWork)
            return out;

        PlatformCredential credential = this.connectionService.resolve(Platform.META);
        String accessToken = credential.getAccessToken();
        AdLibraryQuery query = AdLibraryQuery.runningIn(reachedCountries);

        for (Competitor competitor : competitors) {
            if (competitor == null || competitor.getPageId() == null || competitor.getPageId().isBlank())
                continue;
            out.addAll(this.adLibraryClient.searchByAdvertiser(accessToken, competitor.getPageId(), query));
        }

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank())
                continue;
            out.addAll(this.adLibraryClient.searchByKeyword(accessToken, keyword, query));
        }

        return out;
    }

    private static List<String> distinctPageIds(List<Competitor> competitors) {
        Set<String> ids = new LinkedHashSet<>();
        for (Competitor competitor : competitors) {
            if (competitor != null && competitor.getPageId() != null && !competitor.getPageId().isBlank())
                ids.add(competitor.getPageId());
        }
        return new ArrayList<>(ids);
    }

    /**
     * Resolves the effective client code, mirroring {@code AutonomyConfigService.resolveEffectiveClientCode}
     * / {@code FilesAccessPathService}. Defaults to the caller's own client; a differing target is allowed
     * only for the system client or a managing client administering it.
     */
    private String resolveEffectiveClientCode(String targetClientCode, ContextAuthentication ca) {

        String own = ca.getLoggedInFromClientCode();

        if (targetClientCode == null || targetClientCode.isBlank()
                || StringUtil.safeEquals(targetClientCode.trim(), own))
            return own;

        String target = targetClientCode.trim();
        BigInteger targetClientId = this.securityService.getClientIdByCode(target);

        boolean allowed = ca.isSystemClient()
                || Boolean.TRUE.equals(this.securityService.isUserClientManageClient(ca.getUrlAppCode(),
                        ca.getUser().getId(), ca.getUser().getClientId(), targetClientId));

        if (!allowed)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, CLIENT);

        return target;
    }
}
