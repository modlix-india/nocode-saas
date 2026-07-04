package com.modlix.saas.adzump.service.feedback;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dao.PerformanceSnapshotDao;
import com.modlix.saas.adzump.dto.PerformanceSnapshotEntity;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshotBody;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J10 — the feedback pipeline's service seam. Owns the two operations the loop (J12/J14), the app,
 * and A5 call:
 * <ul>
 * <li>{@link #getSnapshot} — build a fresh snapshot for a window and <b>append</b> it (never mutate a
 * prior one), so the series shows slow-signal maturation. Mutating &rarr; EDIT authority (or the
 * campaign-scoped service token, principal C, when scheduled).</li>
 * <li>{@link #readLatest} / {@link #readSeries} — read the stored series (no authority gate,
 * tenant-scoped at runtime).</li>
 * </ul>
 *
 * <p><b>Tenant model.</b> Every method resolves an effective client code (optional
 * {@code targetClientCode}, defaulting to the caller's own client; a differing target is allowed only
 * for the system client or a managing client administering it — the same pattern as
 * {@code AutonomyConfigService.resolveEffectiveClientCode}) and anchors on the plan: the by-id
 * {@link CampaignPlanService#read} enforces the managed-client gate and yields the authoritative
 * {@code clientCode} the snapshot is stored/scoped under, and {@link #assertPlanClient} stops a
 * manager acting-as client X from touching client Y's plan.
 */
@Service
public class FeedbackService {

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";

    private static final String CLIENT = "client";
    private static final String PLAN = "plan";

    private final SnapshotBuilder snapshotBuilder;
    private final PerformanceSnapshotDao dao;
    private final CampaignPlanService campaignPlanService;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public FeedbackService(SnapshotBuilder snapshotBuilder, PerformanceSnapshotDao dao,
            CampaignPlanService campaignPlanService, FeignAuthenticationService securityService,
            AdzumpMessageResourceService msgService) {

        this.snapshotBuilder = snapshotBuilder;
        this.dao = dao;
        this.campaignPlanService = campaignPlanService;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    /**
     * Builds the snapshot for {@code campaignPlanId} over {@code window} and appends it to the time
     * series. Re-running for the same window appends a fresh {@code takenAt} (facts evolve as slow
     * signal matures) — prior snapshots are never touched (J10 §5.5). Returns the persisted snapshot
     * (with its stored id + {@code takenAt}).
     */
    @PreAuthorize(EDIT)
    public PerformanceSnapshot getSnapshot(ULong campaignPlanId, SnapshotWindow window, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        // Fail fast on a cross-client deny before doing any build work.
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        // The builder reads the plan (PLAN_NOT_FOUND + managed-client gate) and anchors clientCode on it.
        PerformanceSnapshot snapshot = this.snapshotBuilder.build(campaignPlanId, window, targetClientCode);
        this.assertPlanClient(snapshot.getClientCode(), effectiveClient, targetClientCode);

        PerformanceSnapshotEntity saved = this.dao.create(toEntity(snapshot));
        return toDomain(saved);
    }

    /**
     * The most recent snapshot for a plan, or {@code null} when none has been built. No authority
     * gate; tenant-scoped by the plan's managed-client gate.
     */
    public PerformanceSnapshot readLatest(ULong campaignPlanId, String targetClientCode) {

        return this.readLatest(campaignPlanId, (SnapshotWindow) null, targetClientCode);
    }

    /**
     * The most recent <b>stored</b> snapshot for a plan (the J10 {@code get_performance} read, A5 §6),
     * scoped to {@code window} when one is given: the latest stored snapshot whose window matches, else
     * the overall latest (and {@code null} when nothing has been built). No authority gate; tenant-scoped
     * by the plan's managed-client gate.
     *
     * <p><b>Read, not build.</b> This returns a snapshot the J10 build path already produced — it never
     * (re)builds. Building/appending a fresh snapshot is the EDIT-gated {@link #getSnapshot} (or the J14
     * scheduler); a no-authority read must not trigger a mutation, so a window with no stored match falls
     * back to the overall latest rather than materializing a new one.
     */
    public PerformanceSnapshot readLatest(ULong campaignPlanId, SnapshotWindow window, String targetClientCode) {

        String clientCode = this.tenantScope(campaignPlanId, targetClientCode);

        if (window == null || (window.getFrom() == null && window.getTo() == null))
            return toDomain(this.dao.findLatest(clientCode, campaignPlanId));

        // findSeries is oldest-first: the last match seen is the latest snapshot for the window.
        PerformanceSnapshotEntity match = null;
        for (PerformanceSnapshotEntity e : this.dao.findSeries(clientCode, campaignPlanId))
            if (windowMatches(e, window))
                match = e;

        return toDomain(match != null ? match : this.dao.findLatest(clientCode, campaignPlanId));
    }

    /**
     * The full snapshot time series for a plan (oldest first), so the caller sees slow-signal
     * maturation across successive builds. No authority gate; tenant-scoped by the plan's
     * managed-client gate.
     */
    public List<PerformanceSnapshot> readSeries(ULong campaignPlanId, String targetClientCode) {

        String clientCode = this.tenantScope(campaignPlanId, targetClientCode);

        List<PerformanceSnapshotEntity> entities = this.dao.findSeries(clientCode, campaignPlanId);
        List<PerformanceSnapshot> series = new ArrayList<>(entities.size());
        for (PerformanceSnapshotEntity e : entities)
            series.add(toDomain(e));
        return series;
    }

    // ------------------------------------------------------------------------------------------
    // Tenant scoping
    // ------------------------------------------------------------------------------------------

    /**
     * Resolves the effective client, reads the plan (managed-client gate + existence), asserts the
     * plan belongs to a named target client, and returns the plan's authoritative client code — the
     * client snapshots are stored and read under.
     */
    private String tenantScope(ULong campaignPlanId, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        CampaignPlan plan = this.campaignPlanService.read(campaignPlanId); // PLAN_NOT_FOUND + managed-client gate
        this.assertPlanClient(plan.getClientCode(), effectiveClient, targetClientCode);

        return plan.getClientCode();
    }

    /**
     * A stored snapshot matches a requested window when every window field the caller specified equals
     * the snapshot's (unspecified fields are wildcards). Exact-date matching in P3 — the read selects an
     * already-built snapshot, it does not compute a new range.
     */
    private static boolean windowMatches(PerformanceSnapshotEntity e, SnapshotWindow w) {
        if (w.getFrom() != null && !w.getFrom().equals(e.getWindowFrom()))
            return false;
        if (w.getTo() != null && !w.getTo().equals(e.getWindowTo()))
            return false;
        return w.getTimezone() == null || StringUtil.safeEquals(w.getTimezone(), e.getTimezone());
    }

    /**
     * When {@code targetClientCode} names a specific client, the plan must belong to it (a manager
     * acting-as X may not read/build client Y's snapshots). When it is blank the by-id
     * {@link CampaignPlanService#read} managed-client gate already applies.
     */
    private void assertPlanClient(String planClientCode, String effectiveClient, String targetClientCode) {
        if (targetClientCode != null && !targetClientCode.isBlank()
                && !StringUtil.safeEquals(planClientCode, effectiveClient))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, PLAN);
    }

    /**
     * Mirrors {@code AutonomyConfigService.resolveEffectiveClientCode} /
     * {@code FilesAccessPathService.checkAccessNGetClientCode}. Defaults to the caller's own client; a
     * differing target is allowed only for the system client or a managing client administering it.
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

    // ------------------------------------------------------------------------------------------
    // Domain <-> persistence mapping (the window flattens onto columns; grain rows into the body)
    // ------------------------------------------------------------------------------------------

    private static PerformanceSnapshotEntity toEntity(PerformanceSnapshot snapshot) {

        PerformanceSnapshotBody body = new PerformanceSnapshotBody()
                .setProductTemplateId(snapshot.getProductTemplateId())
                .setRollupScore(snapshot.getRollupScore())
                .setGrainRows(snapshot.getGrainRows());

        SnapshotWindow window = snapshot.getWindow();

        return new PerformanceSnapshotEntity()
                .setClientCode(snapshot.getClientCode())
                .setCampaignPlanId(snapshot.getCampaignPlanId())
                .setWindowFrom(window == null ? null : window.getFrom())
                .setWindowTo(window == null ? null : window.getTo())
                .setTimezone(window == null ? null : window.getTimezone())
                .setTakenAt(snapshot.getTakenAt())
                .setBody(body);
    }

    private static PerformanceSnapshot toDomain(PerformanceSnapshotEntity entity) {

        if (entity == null)
            return null;

        PerformanceSnapshotBody body = entity.getBody();

        SnapshotWindow window = new SnapshotWindow()
                .setFrom(entity.getWindowFrom())
                .setTo(entity.getWindowTo())
                .setTimezone(entity.getTimezone());

        return new PerformanceSnapshot()
                .setId(entity.getId())
                .setCampaignPlanId(entity.getCampaignPlanId())
                .setClientCode(entity.getClientCode())
                .setProductTemplateId(body == null ? null : body.getProductTemplateId())
                .setWindow(window)
                .setTakenAt(entity.getTakenAt())
                .setRollupScore(body == null ? 0.0d : body.getRollupScore())
                .setGrainRows(body == null ? List.of() : body.getGrainRows());
    }
}
