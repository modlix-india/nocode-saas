package com.modlix.saas.adzump.service.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.modlix.saas.adzump.dao.CreativeAttributeDao;
import com.modlix.saas.adzump.dto.CreativeAttributeRow;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.leadzump.Grain;
import com.modlix.saas.adzump.model.snapshot.CrmMetrics;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.PlatformMetrics;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.adzump.service.feedback.PolicyScorer;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * Offline unit tests for {@link CreativeScoringService}: the attribute map read is <b>tenant-private</b>
 * (scoped to the caller's own client, never a body value) and a cross-client target the caller cannot
 * manage is denied; {@code recordCreativeAttributes} persists a tagged creative's attributes via the DAO
 * pinned to the resolved client; and {@code getScore} scores off the latest snapshot. A real
 * {@link CreativeScorer} + {@link AdzumpMessageResourceService} are used; the rest are mocked.
 */
class CreativeScoringServiceTest {

    private static final String OWN = "CLI0";
    private static final String RE = "real_estate";
    private static final ULong PLAN_ID = ULong.valueOf(100);
    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();

    private AttributeAttributor attributor;
    private CreativePredictor predictor;
    private CreativeAttributeDao attrDao;
    private FeedbackService feedbackService;
    private FeignAuthenticationService security;
    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;
    private CreativeScoringService service;

    @BeforeEach
    void setUp() {
        this.attributor = mock(AttributeAttributor.class);
        this.predictor = mock(CreativePredictor.class);
        this.attrDao = mock(CreativeAttributeDao.class);
        this.feedbackService = mock(FeedbackService.class);
        this.security = mock(FeignAuthenticationService.class);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn(OWN);

        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);

        this.service = new CreativeScoringService(new CreativeScorer(new PolicyScorer()), this.attributor,
                this.predictor, this.attrDao, this.feedbackService, this.security, MSG);
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    private static AttributeAttribution map(String clientCode) {
        return new AttributeAttribution(clientCode, RE, null, 60.0, List.of(), List.of(), true);
    }

    // ---- tests -----------------------------------------------------------------------------------

    @Test
    void getAttributeMap_isTenantPrivate_scopedToCallersOwnClient() {

        AttributeAttribution own = map(OWN);
        when(this.attributor.attribute(eq(OWN), eq(RE), any())).thenReturn(own);

        AttributeAttribution result = this.service.getAttributeMap(RE, null, null);

        // The attributor is invoked with the caller's OWN client — the map cannot be steered to another.
        ArgumentCaptor<String> clientCap = ArgumentCaptor.forClass(String.class);
        verify(this.attributor).attribute(clientCap.capture(), eq(RE), any());
        assertEquals(OWN, clientCap.getValue());
        assertSame(own, result);
    }

    @Test
    void getAttributeMap_crossClientTheCallerCannotManage_isDenied_beforeAnyCompute() {

        ContextUser user = mock(ContextUser.class);
        when(user.getId()).thenReturn(BigInteger.valueOf(7));
        when(user.getClientId()).thenReturn(BigInteger.ONE);
        when(this.ca.getUser()).thenReturn(user);
        when(this.ca.getUrlAppCode()).thenReturn("adzump");
        when(this.ca.isSystemClient()).thenReturn(false);
        when(this.security.getClientIdByCode("OTHER")).thenReturn(BigInteger.TEN);
        when(this.security.isUserClientManageClient(eq("adzump"), any(), any(), eq(BigInteger.TEN)))
                .thenReturn(false);

        assertThrows(GenericException.class, () -> this.service.getAttributeMap(RE, null, "OTHER"));

        verify(this.attributor, never()).attribute(any(), any(), any());
    }

    @Test
    void recordCreativeAttributes_persistsTaggedAttributes_pinnedToTheResolvedClient() {

        Creative creative = new Creative().setId("cr1")
                .setAttributes(Map.of("angle", "investment_roi", "cta", "book_now"));

        when(this.attrDao.findByCreativeId("cr1")).thenReturn(List.of(
                new CreativeAttributeRow().setClientCode(OWN).setCreativeId("cr1").setAxis("angle")
                        .setValue("investment_roi"),
                new CreativeAttributeRow().setClientCode(OWN).setCreativeId("cr1").setAxis("cta")
                        .setValue("book_now")));

        List<CreativeAttributeRow> persisted = this.service.recordCreativeAttributes("cr1", creative, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreativeAttributeRow>> rowsCap = ArgumentCaptor.forClass(List.class);
        verify(this.attrDao).replaceForCreative(eq(OWN), eq("cr1"), rowsCap.capture());

        List<CreativeAttributeRow> rows = rowsCap.getValue();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> OWN.equals(r.getClientCode()) && "cr1".equals(r.getCreativeId())));
        assertTrue(rows.stream().anyMatch(r -> "angle".equals(r.getAxis()) && "investment_roi".equals(r.getValue())));
        assertTrue(rows.stream().anyMatch(r -> "cta".equals(r.getAxis()) && "book_now".equals(r.getValue())));
        assertEquals(2, persisted.size());
    }

    @Test
    void recordCreativeAttributes_missingCreativeId_isRejected() {
        assertThrows(GenericException.class,
                () -> this.service.recordCreativeAttributes("  ", new Creative(), null));
        verify(this.attrDao, never()).replaceForCreative(any(), any(), any());
    }

    @Test
    void getScore_scoresCreativeOffTheLatestSnapshot() {

        SnapshotRow ad = new SnapshotRow()
                .setGrain(Grain.AD)
                .setAdGrainId(new AdGrainId().setAdId("cr1"))
                .setBlendedScore(77.0)
                .setPlatform(new PlatformMetrics().setSpend(new Money(new BigDecimal("1000"), "INR")))
                .setCrm(new CrmMetrics().setCountByMilestone(Map.of("lead", 30L, "qualified", 12L)).setJunkRate(0.1))
                .setSignalMaturity(SignalMaturity.MATURE);
        PerformanceSnapshot latest = new PerformanceSnapshot().setGrainRows(List.of(ad));

        when(this.feedbackService.readLatest(PLAN_ID, null)).thenReturn(latest);

        CreativeScore score = this.service.getScore(PLAN_ID, "cr1", null);

        assertEquals(77.0d, score.score(), 1e-9);
        assertEquals(42L, score.volume());
        assertTrue(score.judgeable());
    }

    @Test
    void getScore_noSnapshotYet_returnsEmptyScore() {

        when(this.feedbackService.readLatest(PLAN_ID, null)).thenReturn(null);

        CreativeScore score = this.service.getScore(PLAN_ID, "cr1", null);

        assertEquals(0, score.matchedAdRows());
        assertFalse(score.judgeable());
    }

    @Test
    void predict_composesTheTenantMapWithThePredictorGate() {

        AttributeAttribution own = map(OWN);
        when(this.attributor.attribute(eq(OWN), eq(RE), any())).thenReturn(own);
        CreativePrediction expected = new CreativePrediction(0.42, 0.1, true, List.of());
        Creative draft = new Creative().setId("d").setAttributes(Map.of("angle", "scarcity"));
        when(this.predictor.predict(draft, own)).thenReturn(expected);

        CreativePrediction result = this.service.predict(draft, RE, null, null);

        verify(this.predictor).predict(draft, own);
        assertSame(expected, result);
    }
}
