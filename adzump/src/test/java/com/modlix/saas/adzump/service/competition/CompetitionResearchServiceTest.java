package com.modlix.saas.adzump.service.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.modlix.saas.adzump.dao.CompetitionResearchDao;
import com.modlix.saas.adzump.dto.CompetitionResearchEntity;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.competition.Competitor;
import com.modlix.saas.adzump.model.competition.CompetitionResearchRequest;
import com.modlix.saas.adzump.model.competition.CompetitorAd;
import com.modlix.saas.adzump.model.competition.ProxyScore;
import com.modlix.saas.adzump.model.competition.RankedCompetitorAd;
import com.modlix.saas.adzump.model.competition.RankingBasis;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.connection.ConnectionService;
import com.modlix.saas.adzump.vertical.ProxyWeights;
import com.modlix.saas.adzump.vertical.VerticalPlaybook;
import com.modlix.saas.adzump.vertical.VerticalRegistry;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * Offline unit tests for {@link CompetitionResearchService} (J19). The {@link AdLibraryClient} is
 * <b>mocked</b> — no live Ad Library call. They assert: weights come from J5 (the vertical playbook),
 * findings are stored tenant-private and labeled {@link RankingBasis#PROXY} with the honest caveats, the
 * client scope is pinned (a cross-client target the caller cannot manage is denied before any fetch), the
 * read is tenant-scoped, and an empty competitor+keyword set does no live call.
 */
class CompetitionResearchServiceTest {

    private static final String OWN = "CLI0";
    private static final String RE = "real_estate";
    private static final String PRODUCT = "prod1";
    private static final ProxyWeights TUNED = new ProxyWeights(0.5, 0.2, 0.1, 0.1, 0.1);
    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private AdLibraryClient adLibraryClient;
    private BestWorkingProxy proxy;
    private CompetitionResearchDao dao;
    private ConnectionService connectionService;
    private VerticalRegistry verticalRegistry;
    private VerticalPlaybook playbook;
    private FeignAuthenticationService security;
    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;
    private CompetitionResearchService service;

    @BeforeEach
    void setUp() {
        this.adLibraryClient = mock(AdLibraryClient.class);
        this.proxy = mock(BestWorkingProxy.class);
        this.dao = mock(CompetitionResearchDao.class);
        this.connectionService = mock(ConnectionService.class);
        this.verticalRegistry = mock(VerticalRegistry.class);
        this.playbook = mock(VerticalPlaybook.class);
        this.security = mock(FeignAuthenticationService.class);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn(OWN);

        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);

        when(this.verticalRegistry.getOrDefault(any())).thenReturn(this.playbook);
        when(this.dao.create(any())).thenAnswer(inv -> inv.getArgument(0));

        this.service = new CompetitionResearchService(this.adLibraryClient, this.proxy, this.dao,
                this.connectionService, this.verticalRegistry, this.security, MSG);
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    private static CompetitionResearchRequest request(List<Competitor> competitors) {
        return new CompetitionResearchRequest()
                .setVertical(RE)
                .setCompetitors(competitors)
                .setReachedCountries(List.of("IN"));
    }

    private static RankedCompetitorAd rankedAd(String id) {
        return new RankedCompetitorAd(1,
                new CompetitorAd().setId(id).setPageId("P1").setPlatform(Platform.META),
                new ProxyScore(1.0, 0.5, 1.0, 0.0, 0.0, 0.72));
    }

    // ---- research ---------------------------------------------------------------------------------

    @Test
    void research_usesJ5Weights_ranksMinedAds_andPersistsProxyLabeledTenantPrivate() {

        when(this.playbook.competitionProxyWeights()).thenReturn(Optional.of(TUNED));
        when(this.connectionService.resolve(Platform.META))
                .thenReturn(new PlatformCredential().setPlatform(Platform.META).setAccessToken("tok"));

        CompetitorAd mined = new CompetitorAd().setId("m1").setPageId("P1").setPlatform(Platform.META);
        when(this.adLibraryClient.searchByAdvertiser(eq("tok"), eq("P1"), any())).thenReturn(List.of(mined));

        List<RankedCompetitorAd> ranked = List.of(rankedAd("m1"));
        when(this.proxy.rank(anyList(), any(), any(), any())).thenReturn(ranked);

        CompetitionResearchEntity result = this.service.research(
                PRODUCT, request(List.of(new Competitor().setPageId("P1").setPageName("Builder A"))), null);

        // Weights are resolved from J5 (the vertical playbook), not hardcoded.
        ArgumentCaptor<ProxyWeights> weightsCap = ArgumentCaptor.forClass(ProxyWeights.class);
        verify(this.proxy).rank(anyList(), eq(RE), any(LocalDate.class), weightsCap.capture());
        assertEquals(TUNED, weightsCap.getValue());

        // The mined ads are ranked (the token is passed through to the Ad Library facade).
        verify(this.adLibraryClient).searchByAdvertiser(eq("tok"), eq("P1"), any());

        // Persisted tenant-private, labeled PROXY, with weights + ranked ads + caveats.
        ArgumentCaptor<CompetitionResearchEntity> entityCap = ArgumentCaptor.forClass(CompetitionResearchEntity.class);
        verify(this.dao).create(entityCap.capture());
        CompetitionResearchEntity saved = entityCap.getValue();
        assertEquals(OWN, saved.getClientCode(), "pinned to the caller's own client");
        assertEquals(PRODUCT, saved.getProductId());
        assertEquals(RE, saved.getVertical());
        assertEquals(RankingBasis.PROXY, saved.getBody().getRankingBasis(), "labeled proxy, not performance");
        assertEquals(TUNED, saved.getBody().getWeights());
        assertSame(ranked, saved.getBody().getRankedAds());
        assertTrue(saved.getBody().getCompetitorPageIds().contains("P1"));
        assertFalse(saved.getBody().getCaveats().isEmpty(), "honest caveats surfaced");
        assertSame(saved, result);
    }

    @Test
    void research_fallsBackToDefaultWeights_whenVerticalTunesNone() {

        when(this.playbook.competitionProxyWeights()).thenReturn(Optional.empty());
        when(this.proxy.rank(anyList(), any(), any(), any())).thenReturn(List.of());

        // No competitors + no keywords => no live call, so no connection needed.
        this.service.research(PRODUCT, request(List.of()), null);

        ArgumentCaptor<ProxyWeights> weightsCap = ArgumentCaptor.forClass(ProxyWeights.class);
        verify(this.proxy).rank(anyList(), any(), any(), weightsCap.capture());
        assertEquals(ProxyWeights.defaults(), weightsCap.getValue());
    }

    @Test
    void research_noCompetitorsAndNoKeywords_doesNoLiveCall_butStillPersists() {

        when(this.playbook.competitionProxyWeights()).thenReturn(Optional.empty());
        when(this.proxy.rank(anyList(), any(), any(), any())).thenReturn(List.of());

        this.service.research(PRODUCT, request(List.of()), null);

        verify(this.connectionService, never()).resolve(any());
        verify(this.adLibraryClient, never()).searchByAdvertiser(any(), any(), any());
        verify(this.adLibraryClient, never()).searchByKeyword(any(), any(), any());
        verify(this.dao).create(any()); // a finding (empty shortlist) is still recorded
    }

    @Test
    void research_missingProductId_isRejected_beforeAnyWork() {

        assertThrows(GenericException.class,
                () -> this.service.research("  ", request(List.of()), null));

        verify(this.dao, never()).create(any());
        verify(this.connectionService, never()).resolve(any());
    }

    @Test
    void research_crossClientTheCallerCannotManage_isDenied_beforeAnyFetch() {

        ContextUser user = mock(ContextUser.class);
        when(user.getId()).thenReturn(BigInteger.valueOf(7));
        when(user.getClientId()).thenReturn(BigInteger.ONE);
        when(this.ca.getUser()).thenReturn(user);
        when(this.ca.getUrlAppCode()).thenReturn("adzump");
        when(this.ca.isSystemClient()).thenReturn(false);
        when(this.security.getClientIdByCode("OTHER")).thenReturn(BigInteger.TEN);
        when(this.security.isUserClientManageClient(eq("adzump"), any(), any(), eq(BigInteger.TEN)))
                .thenReturn(false);

        assertThrows(GenericException.class, () -> this.service.research(
                PRODUCT, request(List.of(new Competitor().setPageId("P1"))), "OTHER"));

        verify(this.connectionService, never()).resolve(any());
        verify(this.dao, never()).create(any());
    }

    // ---- get --------------------------------------------------------------------------------------

    @Test
    void get_isTenantScopedToOwnClientByDefault() {

        CompetitionResearchEntity stored = new CompetitionResearchEntity().setClientCode(OWN).setProductId(PRODUCT);
        when(this.dao.findLatestByProduct(OWN, PRODUCT)).thenReturn(stored);

        CompetitionResearchEntity result = this.service.get(PRODUCT, null);

        verify(this.dao).findLatestByProduct(OWN, PRODUCT);
        assertSame(stored, result);
    }

    @Test
    void get_missingProductId_isRejected() {
        assertThrows(GenericException.class, () -> this.service.get(null, null));
        verify(this.dao, never()).findLatestByProduct(any(), any());
    }
}
