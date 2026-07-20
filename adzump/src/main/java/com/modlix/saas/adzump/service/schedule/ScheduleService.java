package com.modlix.saas.adzump.service.schedule;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dao.CampaignPlanDao;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.enums.Cadence;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditTriggeredBy;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.ScheduleConfig;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.apply.ActionApplier;
import com.modlix.saas.adzump.service.apply.ApplyResult;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J14 — scheduling: runs the closed loop on a cadence, per campaign, in the account timezone, with
 * <b>no new scheduler infra</b>. The cadence trigger is the reused Modlix platform scheduler; this
 * service is the internal execution it drives, plus the on-demand "run now" that funnels through the
 * <b>same</b> loop-execution path — one path whether scheduled or manual (§5.4).
 *
 * <p><b>The loop (§5.1).</b> {@code run} does J10 snapshot (in the account tz, "yesterday") &rarr; J13
 * {@link ActionApplier#applyLatest} (which internally runs J12 to build the ActionSet, then routes +
 * applies per the campaign's {@code AutonomyConfig} under the J13 guardrails + the
 * {@code adzump.apply.live-enabled} kill-switch). There is no direct call to the AdPlatform SPI or to
 * {@code CampaignService.editLive}/{@code setStatus}: every live change goes through J13.
 *
 * <p><b>Principal C (§5.2).</b> A scheduled run has no user JWT — the <b>campaign row is the
 * principal</b>. {@link #loadCampaign} is a system read by primary key (no user gate); its
 * {@code clientCode} is minted into a {@link ScopedContext} ({@link ServiceTokenMinter#mint}) and
 * carried as the effective client through every downstream read/write. Additionally, before the loop
 * runs, {@link #runScheduled} <b>installs</b> a campaign-scoped, NON-SYS, authenticated
 * {@link ContextAuthentication} ({@link ServiceTokenMinter#authenticate}) on the thread so the EDIT-gated
 * downstream services authorize and the {@code ca}-reads resolve under the campaign's own client with no
 * user present, then clears it in a {@code finally}. A context can only ever drive the one
 * campaign it was minted for, in that campaign's client — enforced in {@link #run}.
 *
 * <p><b>Idempotency + overlap (§5.5).</b> A run keys on {@code (campaignId, window)}; overlapping runs
 * for one campaign are serialized by an in-process per-campaign lock. A re-fire for the same window is
 * idempotent because J13's audit will not double-apply an already-applied ActionSet.
 * TODO(P4.5): a distributed lock (the in-process lock only serializes within one JVM).
 *
 * <p><b>Security (§5.6).</b> The internal/scheduled entries ({@link #optimize(ULong, SnapshotWindow)},
 * {@link #optimize(ScopedContext, ULong, SnapshotWindow)}) carry no {@code @PreAuthorize} — there is no
 * user; the guard is "internal + scoped to the campaign row" (the endpoint is gateway-internal and the
 * service chain whitelists {@code /api/adzump/internal/**}). The human {@link #optimizeOnDemand} carries
 * {@code EDIT} + the effective-client tenant gate before it delegates into the same loop.
 */
@Service
public class ScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleService.class);

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";

    private static final String CLIENT = "client";
    private static final String CAMPAIGN = "campaign";
    private static final String ID = "id";

    private final CampaignPlanDao campaignPlanDao;
    private final CampaignPlanService campaignPlanService;
    private final ServiceTokenMinter serviceTokenMinter;
    private final FeedbackService feedbackService;
    private final ActionApplier actionApplier;
    private final AutonomyConfigService autonomyConfigService;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    /** Per-campaign run locks — serialize overlapping fires so two scheduler ticks can't fight (§5.5). */
    private final ConcurrentHashMap<ULong, Object> runLocks = new ConcurrentHashMap<>();

    public ScheduleService(CampaignPlanDao campaignPlanDao, CampaignPlanService campaignPlanService,
            ServiceTokenMinter serviceTokenMinter, FeedbackService feedbackService, ActionApplier actionApplier,
            AutonomyConfigService autonomyConfigService, FeignAuthenticationService securityService,
            AdzumpMessageResourceService msgService) {

        this.campaignPlanDao = campaignPlanDao;
        this.campaignPlanService = campaignPlanService;
        this.serviceTokenMinter = serviceTokenMinter;
        this.feedbackService = feedbackService;
        this.actionApplier = actionApplier;
        this.autonomyConfigService = autonomyConfigService;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    // =====================================================================================
    // Internal loop entries (no user — the campaign row is the principal)
    // =====================================================================================

    /**
     * The internal loop entry the {@code OptimizeController} (and the delegated on-demand path) call:
     * loads the campaign as a system read, mints its campaign-scoped context, and runs the loop.
     * No {@code @PreAuthorize} — the guard is internal + campaign-scoped (§5.6).
     */
    public OptimizeRun optimize(ULong campaignId, SnapshotWindow window) {
        CampaignPlan campaign = this.loadCampaign(campaignId);
        return this.runScheduled(this.serviceTokenMinter.mint(campaign), campaign, window);
    }

    /**
     * The internal loop entry with a <b>pre-minted</b> {@link ScopedContext} — the shape the P4.5
     * platform scheduler uses once the real service-token issuer mints the context, then calls here. The
     * context must have been minted for {@code campaignId} (identity guard), and {@link #run} further
     * asserts it matches the loaded campaign's client, so a context for campaign A cannot drive a run
     * against campaign B (scope test).
     */
    public OptimizeRun optimize(ScopedContext ctx, ULong campaignId, SnapshotWindow window) {
        if (ctx == null || !ctx.authorizes(campaignId))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, CAMPAIGN);
        CampaignPlan campaign = this.loadCampaign(campaignId);
        return this.runScheduled(ctx, campaign, window);
    }

    // =====================================================================================
    // On-demand ("run now") — human path, SAME loop-execution path
    // =====================================================================================

    /**
     * "Run now" from the app/chat: the human on-demand path. Carries {@code EDIT} + the effective-client
     * tenant gate (the campaign must belong to a client the caller manages), then delegates into the
     * <b>same</b> {@link #run} loop as the scheduler — one loop-execution path, no divergent code (§5.4).
     * The loop itself is attributed {@link AdzumpActionAuditTriggeredBy#SCHEDULER} (it is a headless loop
     * run regardless of who kicked it; per-action human approve/reject attribution lives in J13).
     */
    @PreAuthorize(EDIT)
    public OptimizeRun optimizeOnDemand(ULong campaignId, SnapshotWindow window, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        CampaignPlan campaign = this.campaignPlanService.read(campaignId); // PLAN_NOT_FOUND + managed-client gate
        this.assertPlanClient(campaign, effectiveClient, targetClientCode);

        return this.run(this.serviceTokenMinter.mint(campaign), campaign, window);
    }

    // =====================================================================================
    // Cadence registration seam (§5.3) — the platform scheduler wiring (P4.5/P5) reads this
    // =====================================================================================

    /**
     * The per-campaign optimization {@link Cadence} the platform scheduler registers on — parsed from the
     * effective {@link AutonomyConfig} body via {@link SchedulePolicy} (schema-free: cadence folds into
     * the same JSON body). This is a <b>registration seam</b> only; J14 does NOT build a scheduler
     * (top-risk #4: no new scheduler infra). Defaults to {@link Cadence#ON_DEMAND} when unconfigured.
     */
    public Cadence cadenceFor(ULong campaignId) {
        CampaignPlan campaign = this.loadCampaign(campaignId);
        AutonomyConfig autonomy = this.autonomyConfigService.getEffective(campaignId, campaign.getClientCode());
        return SchedulePolicy.from(autonomy).optimizationCadence();
    }

    // =====================================================================================
    // Principal-C context lifecycle (§5.2) — install the campaign-scoped auth for the no-user run
    // =====================================================================================

    /**
     * Principal-C install/clear lifecycle: a scheduled/internal run has <b>no user JWT</b>, so before the
     * loop runs this builds + <b>installs</b> a campaign-scoped {@link ContextAuthentication}
     * ({@link ServiceTokenMinter#authenticate}) on the running thread — exactly as {@code JWTTokenFilter}
     * does ({@code SecurityContextHolder.getContext().setAuthentication(ca)}) — so the EDIT-gated
     * downstream services ({@link FeedbackService} / {@code OptimizationEngine} inside
     * {@link ActionApplier#applyLatest}) authorize and the {@code ca}-reads
     * ({@code getLoggedInFromClientCode} / {@code getUrlAppCode}, e.g.
     * {@code ConnectionService.resolve}) resolve under the campaign's own client. It is <b>always</b>
     * cleared in a {@code finally} (even on exception), and installed on the <b>same thread</b> that runs
     * the loop (the default {@code MODE_THREADLOCAL} strategy is not inherited by child threads — do not
     * fan the run out without re-installing).
     *
     * <p><b>Do not clobber a user context.</b> If a {@link ContextAuthentication} is already installed on
     * the thread this is a delegated call (defensive — the human {@link #optimizeOnDemand} funnels
     * straight into {@link #run} under its own real user {@code ca}); it is left untouched and the run
     * proceeds under it, so only the pure no-user scheduled path installs-and-clears.
     *
     * <p><b>P4.5 TODO (J11 §9).</b> Same-JVM authorization needs no JWT and this installed context is
     * sufficient for it; a LIVE headless run that reads the leadzump CRM through the entity-processor
     * still needs a forwardable bearer for that EP call (a platform-minted service JWT vs an EP
     * header-only internal endpoint) — deferred because the EP leadzump CRM read endpoint is not built yet.
     */
    private OptimizeRun runScheduled(ScopedContext ctx, CampaignPlan campaign, SnapshotWindow window) {

        // A user context is already installed (delegated call) — run under it, never clobber it.
        if (SecurityContextUtil.getUsersContextAuthentication() != null)
            return this.run(ctx, campaign, window);

        // Pure scheduled path (no user): install the campaign-scoped principal-C context, then ALWAYS
        // clear it in the finally — even if the scope guard or the loop throws (no context bleed).
        SecurityContextHolder.getContext().setAuthentication(this.serviceTokenMinter.authenticate(campaign));
        try {
            return this.run(ctx, campaign, window);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // =====================================================================================
    // The one loop-execution path
    // =====================================================================================

    /**
     * The single loop-execution path (scheduled or manual): scope-guard the context, then under the
     * per-campaign lock build the J10 snapshot for the account-tz window and route+apply the latest
     * ActionSet through J13 headless as the SCHEDULER. The effective client is <b>always the campaign's
     * own</b> ({@code ctx.clientCode()}), never a user-supplied value — so a run cannot touch another
     * client.
     */
    private OptimizeRun run(ScopedContext ctx, CampaignPlan campaign, SnapshotWindow window) {

        ULong campaignId = campaign.getId();

        // Scope enforcement (principal C): the context must be for THIS campaign AND its own client.
        if (ctx == null || !ctx.authorizes(campaignId) || !ctx.authorizesClient(campaign.getClientCode()))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, CAMPAIGN);

        String clientCode = ctx.clientCode();
        String runId = UUID.randomUUID().toString();

        // Serialize overlapping runs for ONE campaign (a re-fire keys on (campaignId, window); the actual
        // dedup of an already-applied ActionSet is J13's audit). In-process; distributed lock is P4.5.
        synchronized (this.lockFor(campaignId)) {

            SnapshotWindow effectiveWindow = window != null ? window : this.yesterdayWindow(campaign);

            // J10: build + append the diagnosis snapshot for the window (in the account tz).
            PerformanceSnapshot snapshot = this.feedbackService.getSnapshot(campaignId, effectiveWindow, clientCode);
            ULong snapshotId = snapshot == null ? null : snapshot.getId();

            // J12 (inside applyLatest) + J13: route the latest ActionSet under the campaign's autonomy +
            // guardrails + the apply.live-enabled kill-switch, headless, as the SCHEDULER. A re-fire is
            // idempotent — J13's audit will not double-apply an already-applied ActionSet.
            ApplyResult applyResult = this.actionApplier.applyLatest(campaignId,
                    AdzumpActionAuditTriggeredBy.SCHEDULER, clientCode);

            logger.info("adzump.schedule.run campaign={} client={} window={}..{} tz={} applied={} runId={}",
                    campaignId, clientCode, effectiveWindow.getFrom(), effectiveWindow.getTo(),
                    effectiveWindow.getTimezone(), applyResult == null ? 0L : applyResult.appliedCount(), runId);

            return OptimizeRun.of(campaignId, effectiveWindow, snapshotId, applyResult, runId);
        }
    }

    // =====================================================================================
    // Window + timezone (§5.3 / DESIGN §9.5)
    // =====================================================================================

    /**
     * "Yesterday" in the ad account reporting timezone (DESIGN §9.5), so the day boundary matches the
     * platform's billing/insights day (and the CRM outcome window evaluates in the same frame). The tz is
     * read from the plan's {@code schedule.timezone} (inherited from the ad account, CONTRACT §1.1); an
     * absent/unparseable tz falls back to UTC.
     */
    private SnapshotWindow yesterdayWindow(CampaignPlan campaign) {
        ZoneId zone = resolveZone(accountTimezone(campaign));
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        return new SnapshotWindow().setFrom(yesterday).setTo(yesterday).setTimezone(zone.getId());
    }

    private static String accountTimezone(CampaignPlan campaign) {
        CampaignPlanBody body = campaign == null ? null : campaign.getBody();
        ScheduleConfig schedule = body == null ? null : body.getSchedule();
        return schedule == null ? null : schedule.getTimezone();
    }

    private static ZoneId resolveZone(String tz) {
        if (tz == null || tz.isBlank())
            return ZoneOffset.UTC;
        try {
            return ZoneId.of(tz.trim());
        } catch (RuntimeException e) {
            return ZoneOffset.UTC;
        }
    }

    // =====================================================================================
    // Campaign load + tenancy
    // =====================================================================================

    /**
     * System read of the campaign by primary key — the bootstrap that makes the campaign the principal
     * (no user gate; there is no user JWT on a scheduled run). Scope is then pinned to the campaign's own
     * client throughout. Returns the plan or throws {@code PLAN_NOT_FOUND}.
     */
    private CampaignPlan loadCampaign(ULong campaignId) {

        if (campaignId == null)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "campaignId");

        List<CampaignPlan> rows = this.campaignPlanDao.readAll(FilterCondition.make(ID, campaignId));
        if (rows.isEmpty())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    AdzumpMessageResourceService.PLAN_NOT_FOUND, campaignId);

        return rows.getFirst();
    }

    private Object lockFor(ULong campaignId) {
        return this.runLocks.computeIfAbsent(campaignId, k -> new Object());
    }

    private void assertPlanClient(CampaignPlan plan, String effectiveClient, String targetClientCode) {
        if (targetClientCode != null && !targetClientCode.isBlank()
                && !StringUtil.safeEquals(plan.getClientCode(), effectiveClient))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, CAMPAIGN);
    }

    /**
     * Resolves the effective client code for the human on-demand path, mirroring
     * {@code CampaignService.resolveEffectiveClientCode}. Defaults to the caller's own client; a
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
}
