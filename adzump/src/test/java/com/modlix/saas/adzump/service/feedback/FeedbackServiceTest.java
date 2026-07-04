package com.modlix.saas.adzump.service.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.modlix.saas.adzump.dao.PerformanceSnapshotDao;
import com.modlix.saas.adzump.dto.PerformanceSnapshotEntity;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * Offline unit tests for the J10 {@link FeedbackService}: the build path <b>appends</b> to the time
 * series (never mutates a prior snapshot), the reads return the persisted series/null, and the
 * effective-client tenant gate denies a foreign client before any work. The {@link SnapshotBuilder}
 * and the DAO are mocked; a real {@link AdzumpMessageResourceService} raises real guard failures.
 */
class FeedbackServiceTest {

    private static final AdzumpMessageResourceService MSG = new AdzumpMessageResourceService();
    private static final ULong PLAN_ID = ULong.valueOf(100);

    private SnapshotBuilder snapshotBuilder;
    private PerformanceSnapshotDao dao;
    private CampaignPlanService campaignPlanService;
    private FeignAuthenticationService security;
    private ContextAuthentication ca;
    private MockedStatic<SecurityContextUtil> securityCtx;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        this.snapshotBuilder = mock(SnapshotBuilder.class);
        this.dao = mock(PerformanceSnapshotDao.class);
        this.campaignPlanService = mock(CampaignPlanService.class);
        this.security = mock(FeignAuthenticationService.class);

        this.ca = mock(ContextAuthentication.class);
        when(this.ca.getLoggedInFromClientCode()).thenReturn("CLI0");

        this.securityCtx = Mockito.mockStatic(SecurityContextUtil.class);
        this.securityCtx.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(this.ca);

        this.service = new FeedbackService(this.snapshotBuilder, this.dao, this.campaignPlanService,
                this.security, MSG);
    }

    @AfterEach
    void tearDown() {
        this.securityCtx.close();
    }

    private static CampaignPlan plan() {
        CampaignPlan plan = new CampaignPlan().setClientCode("CLI0").setName("Plan");
        plan.setId(PLAN_ID);
        return plan;
    }

    private static PerformanceSnapshot domainSnapshot(LocalDate from, LocalDate to) {
        return new PerformanceSnapshot()
                .setCampaignPlanId(PLAN_ID)
                .setClientCode("CLI0")
                .setProductTemplateId("tmpl-re")
                .setWindow(new SnapshotWindow().setFrom(from).setTo(to).setTimezone("Asia/Calcutta"))
                .setTakenAt(LocalDateTime.now())
                .setRollupScore(50.0)
                .setGrainRows(List.of());
    }

    // =====================================================================================

    @Test
    void getSnapshot_appendsEachBuild_asAnImmutableSeries() {

        List<PerformanceSnapshotEntity> store = new ArrayList<>();
        AtomicInteger idSeq = new AtomicInteger(0);

        // The DAO append: assign an id and add a NEW row; prior rows are never touched.
        when(this.dao.create(any())).thenAnswer(inv -> {
            PerformanceSnapshotEntity e = inv.getArgument(0);
            e.setId(ULong.valueOf(idSeq.incrementAndGet()));
            if (e.getTakenAt() == null)
                e.setTakenAt(LocalDateTime.now());
            store.add(e);
            return e;
        });
        when(this.dao.findSeries(eq("CLI0"), eq(PLAN_ID))).thenReturn(store);
        when(this.campaignPlanService.read(PLAN_ID)).thenReturn(plan());

        when(this.snapshotBuilder.build(eq(PLAN_ID), any(), any())).thenReturn(
                domainSnapshot(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
                domainSnapshot(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));

        PerformanceSnapshot first = this.service.getSnapshot(PLAN_ID, null, null);
        PerformanceSnapshot second = this.service.getSnapshot(PLAN_ID, null, null);

        assertEquals(ULong.valueOf(1), first.getId());
        assertEquals(ULong.valueOf(2), second.getId());
        assertNotNull(first.getTakenAt());

        // Two appends -> two rows in the series, distinct ids, oldest first.
        verify(this.dao, times(2)).create(any());
        List<PerformanceSnapshot> series = this.service.readSeries(PLAN_ID, null);
        assertEquals(2, series.size());
        assertEquals(ULong.valueOf(1), series.get(0).getId());
        assertEquals(ULong.valueOf(2), series.get(1).getId());
        // The body round-trips through persistence.
        assertEquals("tmpl-re", series.get(0).getProductTemplateId());
        assertEquals(50.0d, series.get(0).getRollupScore(), 1e-9);
    }

    @Test
    void readLatest_returnsNull_whenNothingBuiltYet() {

        when(this.campaignPlanService.read(PLAN_ID)).thenReturn(plan());
        when(this.dao.findLatest("CLI0", PLAN_ID)).thenReturn(null);

        assertNull(this.service.readLatest(PLAN_ID, null));
    }

    @Test
    void getSnapshot_tenantDeny_foreignClientTheCallerCannotManage_forbidden_beforeAnyBuild() {

        ContextUser user = mock(ContextUser.class);
        when(user.getId()).thenReturn(BigInteger.valueOf(7));
        when(user.getClientId()).thenReturn(BigInteger.ONE);
        when(this.ca.getUser()).thenReturn(user);
        when(this.ca.getUrlAppCode()).thenReturn("adzump");
        when(this.ca.isSystemClient()).thenReturn(false);
        when(this.security.getClientIdByCode("OTHER")).thenReturn(BigInteger.TEN);
        when(this.security.isUserClientManageClient(eq("adzump"), any(), any(), eq(BigInteger.TEN)))
                .thenReturn(false);

        assertThrows(GenericException.class, () -> this.service.getSnapshot(PLAN_ID, null, "OTHER"));

        // Denied at effective-client resolution, before the snapshot is built or persisted.
        verify(this.snapshotBuilder, never()).build(any(), any(), any());
        verify(this.dao, never()).create(any());
    }
}
