package com.modlix.saas.adzump.service.optimize;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SignalMaturity;
import com.modlix.saas.adzump.model.snapshot.SnapshotRow;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.PerformancePolicyService;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.adzump.service.optimize.SignificanceGate.GateConfig;
import com.modlix.saas.adzump.service.optimize.SignificanceVerdict.GateOutcome;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J12 — the optimization engine, the brain of the loop. Turns the latest J10
 * {@link PerformanceSnapshot} + the {@link CampaignPlan} into a ranked, gated {@link ActionSet}:
 * runs every {@link DimensionAnalyzer}, passes each candidate through the {@link SignificanceGate}
 * (never react to noise), ranks the survivors by their expected objective delta, caps them to the
 * per-run limit, and reports the projected objective ({@link Objective}).
 *
 * <p><b>Recommend-mode only (P3):</b> the engine <b>produces and gates</b> the ActionSet; it applies
 * nothing. Every {@link Action#requiresApproval()} is {@code true}. Autonomy routing (which actions may
 * auto-apply) and the mutation itself are J13 (P4); the approval-queue row is likewise J13 — the
 * recommendations here are computed <b>on-demand</b> and returned in-memory, no table (TODO(J13)).
 *
 * <p><b>Tenancy/security.</b> {@link #optimize} mutates (it may build+append a snapshot) so it carries
 * EDIT authority and resolves the effective client (optional {@code targetClientCode}) exactly like
 * {@code FeedbackService}. {@link #getRecommendations} is a read (no {@code @PreAuthorize}), tenant-
 * scoped through the plan-anchored J10 reads. Neither holds any mutation authority over the platforms
 * (that is J13, EDIT + guardrails).
 */
@Service
public class OptimizationEngine {

    private static final Logger logger = LoggerFactory.getLogger(OptimizationEngine.class);

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";
    private static final String CLIENT = "client";

    /** Reads the caller-supplied {@code change} payload into its typed {@link ActionChange} for the echo. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<DimensionAnalyzer> analyzers;
    private final SignificanceGate significanceGate;
    private final Objective objective;
    private final FeedbackService feedbackService;
    private final CampaignPlanService campaignPlanService;
    private final PerformancePolicyService performancePolicyService;
    private final AutonomyConfigService autonomyConfigService;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public OptimizationEngine(List<DimensionAnalyzer> analyzers, SignificanceGate significanceGate,
            Objective objective, FeedbackService feedbackService, CampaignPlanService campaignPlanService,
            PerformancePolicyService performancePolicyService, AutonomyConfigService autonomyConfigService,
            FeignAuthenticationService securityService, AdzumpMessageResourceService msgService) {

        this.analyzers = analyzers;
        this.significanceGate = significanceGate;
        this.objective = objective;
        this.feedbackService = feedbackService;
        this.campaignPlanService = campaignPlanService;
        this.performancePolicyService = performancePolicyService;
        this.autonomyConfigService = autonomyConfigService;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    /**
     * Produces the ActionSet for a campaign over a window. When {@code window} is given a fresh J10
     * snapshot is built and appended (the diagnosis is taken on current facts); otherwise the latest
     * stored snapshot is used. Everything is proposed with {@code requiresApproval = true}; nothing is
     * applied. EDIT authority (or the campaign-scoped service token when scheduled).
     */
    @PreAuthorize(EDIT)
    public ActionSet optimize(ULong campaignPlanId, SnapshotWindow window, String targetClientCode) {

        // Fail fast on a cross-client deny before doing any build work (mirrors FeedbackService).
        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        this.resolveEffectiveClientCode(targetClientCode, ca);

        PerformanceSnapshot snapshot = window != null
                ? this.feedbackService.getSnapshot(campaignPlanId, window, targetClientCode)
                : this.feedbackService.readLatest(campaignPlanId, targetClientCode);

        return this.buildActionSet(campaignPlanId, snapshot, targetClientCode);
    }

    /**
     * The approval-queue feed (J12 §6): the current recommendations for a campaign, computed on-demand
     * from the latest stored snapshot (no table). No authority gate; tenant-scoped by the plan-anchored
     * J10 read below.
     */
    public ActionSet getRecommendations(ULong campaignId) {

        PerformanceSnapshot snapshot = this.feedbackService.readLatest(campaignId, null);
        return this.buildActionSet(campaignId, snapshot, null);
    }

    /**
     * The {@code get_recommendations} read (A5 §6): the on-demand ActionSet for a campaign over
     * {@code window}, computed from the latest stored snapshot for that window (no build, no table). No
     * authority gate; tenant-scoped by the plan-anchored J10 read. Every action is
     * {@code requiresApproval = true}; nothing is applied.
     */
    public ActionSet getRecommendations(ULong campaignId, SnapshotWindow window, String targetClientCode) {

        PerformanceSnapshot snapshot = this.feedbackService.readLatest(campaignId, window, targetClientCode);
        return this.buildActionSet(campaignId, snapshot, targetClientCode);
    }

    /**
     * The {@code propose_action} entry (A5 §5.3 / J12 §5): runs a <b>caller-proposed</b> candidate through
     * the same {@link SignificanceGate} + {@link Objective} as an analyzer-born one and returns it gated
     * (as a one-action {@link ActionSet}, {@code requiresApproval = true}) or suppressed (as a one-entry
     * {@code suppressed} list carrying the reason). A5 uses this for genuinely new actions its narration
     * invents, so they still go through the gates rather than around them.
     *
     * <p><b>Recommend-mode only:</b> this APPLIES NOTHING — no J8 lifecycle / platform-SPI mutation. The
     * statistical context the gate judges on is derived from the target's snapshot row (not trusted from
     * the caller), so a proposal cannot be talked past the gate. Mutating authority ({@code EDIT}) because
     * it is the write-intent entry even though the write itself is deferred.
     *
     * <p>TODO(J13/P4): autonomy routing (which gated proposals may auto-apply in HYBRID/AUTONOMOUS) and
     * the actual guarded apply + {@code adzump_action_audit} row.
     */
    @PreAuthorize(EDIT)
    public ActionSet proposeAction(ULong campaignPlanId, ProposedAction proposed, String targetClientCode) {

        // Fail fast on a cross-client deny before any read (mirrors optimize()).
        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        this.resolveEffectiveClientCode(targetClientCode, ca);

        if (proposed == null || proposed.type() == null)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "type");
        if (proposed.target() == null)
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "target");

        // Read the facts + effective config (the plan read re-runs the by-id managed-client tenant gate).
        PerformanceSnapshot snapshot = this.feedbackService.readLatest(campaignPlanId, targetClientCode);
        CampaignPlan plan = this.campaignPlanService.read(campaignPlanId);
        PerformancePolicy policy = this.performancePolicyService.getEffective(campaignPlanId, targetClientCode);
        AutonomyConfig autonomy = this.autonomyConfigService.getEffective(campaignPlanId, targetClientCode);

        GateConfig gateConfig = this.significanceGate.config(policy, autonomy);
        AnalyzerContext ctx = new AnalyzerContext(snapshot, plan, policy);

        double delta = proposed.expectedDelta() != null ? proposed.expectedDelta() : 0.0d;
        double confidence = proposed.confidence() != null ? proposed.confidence() : 0.6d;
        Risk risk = proposed.risk() != null ? proposed.risk() : Risk.MED;
        ActionChange change = this.toActionChange(proposed.type(), proposed.change());

        Candidate candidate = this.candidateFrom(proposed, ctx, delta, confidence, risk, change);
        SignificanceVerdict verdict = this.significanceGate.evaluate(candidate, gateConfig);

        double before = this.objective.before(snapshot);
        ULong snapshotId = snapshot == null ? null : snapshot.getId();

        // APPLIES NOTHING (recommend-mode): the verdict is returned; J13 (P4) owns routing + apply.
        if (verdict.passed()) {
            Action action = new Action(proposed.type(), proposed.target(), change, proposed.rationale(),
                    delta, confidence, verdict, risk, true);
            double after = this.objective.projectedAfter(before, List.of(action));
            return new ActionSet(campaignPlanId, snapshotId, LocalDateTime.now(), List.of(action), List.of(),
                    before, after);
        }

        SuppressedCandidate suppressed = new SuppressedCandidate(proposed.type(), proposed.target(), verdict,
                proposed.rationale());
        logger.info("adzump.optimize.propose.suppressed campaign={} type={} reason={} detail={}",
                campaignPlanId, proposed.type(), verdict.outcome(), verdict.detail());
        return new ActionSet(campaignPlanId, snapshotId, LocalDateTime.now(), List.of(), List.of(suppressed),
                before, before);
    }

    // ------------------------------------------------------------------------------------------
    // The pipeline: analyze -> gate -> rank -> cap -> project
    // ------------------------------------------------------------------------------------------

    private ActionSet buildActionSet(ULong campaignPlanId, PerformanceSnapshot snapshot, String targetClientCode) {

        double before = this.objective.before(snapshot);

        if (snapshot == null || snapshot.getGrainRows() == null || snapshot.getGrainRows().isEmpty()) {
            // Nothing measured yet -> nothing to optimize (explainable empty set).
            return ActionSet.empty(campaignPlanId, snapshot == null ? null : snapshot.getId(), before);
        }

        // The plan read re-runs the by-id managed-client tenant gate; policy/autonomy resolve the same
        // effective client the J10 read used.
        CampaignPlan plan = this.campaignPlanService.read(campaignPlanId);
        PerformancePolicy policy = this.performancePolicyService.getEffective(campaignPlanId, targetClientCode);
        AutonomyConfig autonomy = this.autonomyConfigService.getEffective(campaignPlanId, targetClientCode);

        AnalyzerContext ctx = new AnalyzerContext(snapshot, plan, policy);
        GateConfig gateConfig = this.significanceGate.config(policy, autonomy);

        List<Action> passed = new ArrayList<>();
        List<SuppressedCandidate> suppressed = new ArrayList<>();

        for (DimensionAnalyzer analyzer : this.analyzers) {
            for (Candidate candidate : analyzer.analyze(ctx)) {
                SignificanceVerdict verdict = this.significanceGate.evaluate(candidate, gateConfig);
                if (verdict.passed()) {
                    passed.add(toAction(candidate, verdict));
                } else {
                    suppressed.add(new SuppressedCandidate(candidate.type(), candidate.target(), verdict,
                            candidate.rationale()));
                    // Explainable no-op: log WHY a candidate did not fire.
                    logger.info("adzump.optimize.suppressed campaign={} type={} reason={} detail={}",
                            campaignPlanId, candidate.type(), verdict.outcome(), verdict.detail());
                }
            }
        }

        // Rank by expected objective delta (descending).
        passed.sort(Comparator.comparingDouble(Action::expectedDelta).reversed());

        // Cap to the per-run limit; overflow is deferred, not dropped (surfaced as suppressed).
        int cap = gateConfig.maxChangesPerRun();
        if (cap >= 0 && passed.size() > cap) {
            List<Action> capped = new ArrayList<>(passed.subList(0, cap));
            for (Action overflow : passed.subList(cap, passed.size())) {
                SignificanceVerdict v = SignificanceVerdict.suppressed(GateOutcome.MAX_CHANGES_EXCEEDED,
                        overflow.significance().sampleSize(), overflow.significance().confidence(),
                        "beyond max-changes-per-run " + cap);
                suppressed.add(new SuppressedCandidate(overflow.type(), overflow.target(), v, overflow.rationale()));
                logger.info("adzump.optimize.deferred campaign={} type={} reason=MAX_CHANGES_EXCEEDED cap={}",
                        campaignPlanId, overflow.type(), cap);
            }
            passed = capped;
        }

        double after = this.objective.projectedAfter(before, passed);

        return new ActionSet(campaignPlanId, snapshot.getId(), LocalDateTime.now(), List.copyOf(passed),
                List.copyOf(suppressed), before, after);
    }

    /** Every proposal is recommend-only in P3: {@code requiresApproval} is always true. */
    private static Action toAction(Candidate c, SignificanceVerdict verdict) {
        return new Action(c.type(), c.target(), c.change(), c.rationale(), c.expectedDelta(), c.confidence(),
                verdict, c.risk(), true);
    }

    // ------------------------------------------------------------------------------------------
    // Caller-proposed candidate: the gate context is derived from the target's snapshot row, so a
    // proposal is judged on the same statistical footing as an analyzer-born one (never trusted from
    // the caller). Absent a baseline comparison the gate skips the proportion test (as the bid/creative
    // analyzers do) and judges on volume + maturity + do-no-harm.
    // ------------------------------------------------------------------------------------------

    private Candidate candidateFrom(ProposedAction proposed, AnalyzerContext ctx, double delta, double confidence,
            Risk risk, ActionChange change) {

        SnapshotRow row = findRow(ctx.snapshot(), proposed.target());

        long volume = AnalyzerContext.clicks(row);
        SignalMaturity maturity = row != null && row.getSignalMaturity() != null
                ? row.getSignalMaturity()
                : SignalMaturity.FAST_ONLY;

        // A pause of a converter is a kill (waits for MATURE); a pause of non-converting waste is a trim.
        boolean kill = proposed.type() == AdzumpActionAuditActionType.PAUSE_ENTITY
                && AnalyzerContext.isConverter(row);
        boolean onlyConverter = kill && row != null && ctx.converterCountAt(row.getGrain()) <= 1L;

        return new Candidate(proposed.type(), proposed.target(), change, proposed.rationale(), delta, confidence,
                risk, kill, onlyConverter, maturity, volume, 0L, 0L, 0L, 0L);
    }

    /** The snapshot row the proposal targets, matched on the most specific id the target carries. */
    private static SnapshotRow findRow(PerformanceSnapshot snapshot, AdGrainId target) {
        if (snapshot == null || snapshot.getGrainRows() == null || target == null)
            return null;
        for (SnapshotRow row : snapshot.getGrainRows())
            if (row != null && matches(row.getAdGrainId(), target))
                return row;
        return null;
    }

    private static boolean matches(AdGrainId rowId, AdGrainId target) {
        if (rowId == null)
            return false;
        if (target.getAdId() != null)
            return target.getAdId().equals(rowId.getAdId());
        if (target.getAdSetId() != null)
            return target.getAdSetId().equals(rowId.getAdSetId());
        if (target.getCampaignId() != null)
            return target.getCampaignId().equals(rowId.getCampaignId());
        return false;
    }

    /** Deserializes the caller's raw {@code change} JSON into its typed {@link ActionChange} per type. */
    private ActionChange toActionChange(AdzumpActionAuditActionType type, JsonNode node) {

        if (node == null || node.isNull() || !node.isObject())
            return null;
        try {
            return switch (type) {
                case SHIFT_BUDGET -> MAPPER.treeToValue(node, ActionChange.BudgetShift.class);
                case ADJUST_BID -> MAPPER.treeToValue(node, ActionChange.BidChange.class);
                case REFINE_AUDIENCE -> MAPPER.treeToValue(node, ActionChange.AudienceRefinement.class);
                case ADD_NEGATIVE_KEYWORD -> MAPPER.treeToValue(node, ActionChange.NegativeKeyword.class);
                case PAUSE_ENTITY -> MAPPER.treeToValue(node, ActionChange.Pause.class);
                case ROTATE_CREATIVE -> MAPPER.treeToValue(node, ActionChange.CreativeRotation.class);
                case REQUEST_VARIANT -> MAPPER.treeToValue(node, ActionChange.VariantRequest.class);
            };
        } catch (JsonProcessingException e) {
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "change");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Tenancy (mirrors FeedbackService / AutonomyConfigService.resolveEffectiveClientCode)
    // ------------------------------------------------------------------------------------------

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
