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

import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
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
