package com.modlix.saas.adzump.service.experiment;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dao.ExperimentDao;
import com.modlix.saas.adzump.dto.Experiment;
import com.modlix.saas.adzump.dto.ExperimentReadout;
import com.modlix.saas.adzump.dto.ExperimentVariant;
import com.modlix.saas.adzump.dto.VariantOutcome;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditTriggeredBy;
import com.modlix.saas.adzump.jooq.enums.AdzumpExperimentStatus;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.Creative;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.leadzump.AdGrainId;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.apply.ActionApplier;
import com.modlix.saas.adzump.service.apply.ApplyPlan;
import com.modlix.saas.adzump.service.apply.AutonomyPolicy;
import com.modlix.saas.adzump.service.apply.AutonomyRouter;
import com.modlix.saas.adzump.service.creative.CreativeScore;
import com.modlix.saas.adzump.service.creative.CreativeScoringService;
import com.modlix.saas.adzump.service.optimize.Action;
import com.modlix.saas.adzump.service.optimize.ActionChange;
import com.modlix.saas.adzump.service.optimize.ActionSet;
import com.modlix.saas.adzump.service.optimize.Risk;
import com.modlix.saas.adzump.service.optimize.SignificanceVerdict;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J21 — the creative-experiment engine (all logic, no CRUD). Runs controlled variant tests so J20's
 * attribute attribution rests on <b>real counterfactuals</b>, then promotes the winner and retires the
 * losers <b>through J13</b>, feeding the causal result back to J20.
 *
 * <p>The lifecycle:
 * <ol>
 * <li>{@link #design} — persist a DESIGNED experiment whose arms vary <b>exactly one</b> attribute axis
 * (attribute-isolation, so the readout's lift is attributable to that axis, not a tangle), with even
 * allocation by default and the blended objective at creative grain as the metric.</li>
 * <li>{@link #start} — rotate the arms live via a ROTATE_CREATIVE apply <b>routed through
 * {@link ActionApplier}</b> (never J8 directly), under autonomy + guardrails + the kill-switch; RUNNING.</li>
 * <li>{@link #readout} — measure each arm at the creative grain (J10/J20 {@code getScore}) and run a
 * <b>maturity-aware</b> two-proportion significance test. A winner is never declared on immature (fast-only)
 * signal; the experiment ends on significance or the volume/duration cap — never unbounded — falling to
 * INCONCLUSIVE rather than a false winner.</li>
 * <li>{@link #decide} — PROMOTE the winner (a {@code SHIFT_BUDGET}) and RETIRE the losers (a
 * {@code PAUSE_ENTITY}), <b>both guardrailed applies through {@link ActionApplier}</b>, then record the
 * causal attribute result to J20 via {@link CreativeScoringService}; APPLIED.</li>
 * </ol>
 *
 * <p><b>Money-safety.</b> The experiment never touches a platform directly: every live change (rotation,
 * promote, retire) goes through the one J13 apply spine, so it is bound by the campaign's AutonomyConfig
 * caps and the {@code adzump.apply.live-enabled} kill-switch — an experiment cannot exceed the campaign's
 * spend limits. {@code INCONCLUSIVE} is a valid, logged outcome (no false learning).
 *
 * <p><b>Security / tenancy.</b> The mutating verbs carry {@code EDIT}; the list read carries none and is
 * tenant-scoped at runtime. Every method resolves an effective client (optional {@code targetClientCode})
 * and anchors on the plan's managed-client gate, so an experiment — and the account attribute wins it feeds
 * into J20 — stays tenant-private (mirrors {@link CreativeScoringService} / {@code FeedbackService}).
 */
@Service
public class ExperimentService {

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";

    private static final String CLIENT = "client";
    private static final String PLAN = "plan";
    private static final String EXPERIMENT = "Experiment";
    private static final String CAMPAIGN_PLAN_ID = "campaignPlanId";
    private static final String VARIANTS = "variants";

    static final String DEFAULT_METRIC = "blendedScore@creative";
    static final int DEFAULT_MIN_VOLUME_PER_VARIANT = 300;
    static final int DEFAULT_MAX_DURATION_DAYS = 14;

    private final ExperimentDao experimentDao;
    private final CampaignPlanService campaignPlanService;
    private final CreativeScoringService creativeScoringService;
    private final ActionApplier actionApplier;
    private final AutonomyRouter autonomyRouter;
    private final AutonomyConfigService autonomyConfigService;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public ExperimentService(ExperimentDao experimentDao, CampaignPlanService campaignPlanService,
            CreativeScoringService creativeScoringService, ActionApplier actionApplier,
            AutonomyRouter autonomyRouter, AutonomyConfigService autonomyConfigService,
            FeignAuthenticationService securityService, AdzumpMessageResourceService msgService) {

        this.experimentDao = experimentDao;
        this.campaignPlanService = campaignPlanService;
        this.creativeScoringService = creativeScoringService;
        this.actionApplier = actionApplier;
        this.autonomyRouter = autonomyRouter;
        this.autonomyConfigService = autonomyConfigService;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    // =====================================================================================
    // Design (explore, not random churn)
    // =====================================================================================

    /**
     * Designs a controlled experiment (§5.2): validates attribute-isolation across the arms (exactly one
     * axis differs, all others held constant), defaults allocation to even and the metric to the blended
     * objective at creative grain, and persists it {@code DESIGNED}. Rejects a confounded (multi-axis) or
     * degenerate (no-contrast / &lt;2-arm) variant set so the readout's lift can only be attributed to the
     * one axis under test. The arms come from A4 (creativeIds + attributes passed in); A4 generation is out
     * of band.
     */
    @PreAuthorize(EDIT)
    public Experiment design(ULong campaignPlanId, String hypothesis, List<ExperimentVariant> variants,
            String metric, Integer minVolumePerVariant, Integer maxDurationDays, String targetClientCode) {

        ContextAuthentication ca = ca();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca); // fail-fast tenant deny

        if (campaignPlanId == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, CAMPAIGN_PLAN_ID);

        CampaignPlan plan = this.campaignPlanService.read(campaignPlanId); // PLAN_NOT_FOUND + managed-client gate
        this.assertPlanClient(plan, effectiveClient, targetClientCode);

        if (variants == null || variants.isEmpty())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, VARIANTS);

        this.validateAttributeIsolation(variants); // rejects confounded / no-contrast / <2-arm sets
        this.applyEvenAllocationIfNeeded(variants);

        Experiment experiment = new Experiment()
                .setClientCode(plan.getClientCode())
                .setCampaignPlanId(campaignPlanId)
                .setHypothesis(hypothesis)
                .setMetric(StringUtil.safeIsBlank(metric) ? DEFAULT_METRIC : metric.trim())
                .setMinVolumePerVariant(positiveOr(minVolumePerVariant, DEFAULT_MIN_VOLUME_PER_VARIANT))
                .setMaxDurationDays(positiveOr(maxDurationDays, DEFAULT_MAX_DURATION_DAYS))
                .setStatus(AdzumpExperimentStatus.DESIGNED)
                .setVariants(variants)
                .setReadout(null)
                .setStartedAt(null)
                .setEndedAt(null);

        if (ca.getUser() != null)
            experiment.setCreatedBy(ULong.valueOf(ca.getUser().getId()));

        return this.experimentDao.create(experiment);
    }

    // =====================================================================================
    // Start (rotate live via J13, never J8 directly)
    // =====================================================================================

    /**
     * Starts a DESIGNED experiment (§5.3): rotates the arms live through the one J13 apply spine — a
     * {@code ROTATE_CREATIVE} per arm routed by autonomy + guardrails + the kill-switch, <b>never</b>
     * {@code CampaignService} directly — and moves the experiment to {@code RUNNING} with a start time.
     */
    @PreAuthorize(EDIT)
    public Experiment start(ULong id, String targetClientCode) {

        Experiment experiment = this.loadForEdit(id, targetClientCode);
        this.requireStatus(experiment, AdzumpExperimentStatus.DESIGNED);

        ActionSet rotation = this.buildRotationActionSet(experiment);
        this.routeAndApply(experiment.getCampaignPlanId(), rotation, AdzumpActionAuditTriggeredBy.USER,
                targetClientCode);

        experiment.setStatus(AdzumpExperimentStatus.RUNNING);
        experiment.setStartedAt(LocalDateTime.now());
        this.stampUpdatedBy(experiment);
        return this.experimentDao.update(experiment);
    }

    // =====================================================================================
    // Readout (significance, maturity-aware) + stop
    // =====================================================================================

    /**
     * Measures each arm at the creative grain and computes a maturity-aware readout (§5.3). Ends the
     * experiment on significance or the volume/duration cap (never unbounded); a winner is declared only on
     * mature signal, else the outcome is {@code INCONCLUSIVE}. While no cap is reached and no mature win
     * exists, the experiment stays {@code RUNNING} with an interim readout stored.
     */
    @PreAuthorize(EDIT)
    public Experiment readout(ULong id, String targetClientCode) {
        Experiment experiment = this.loadForEdit(id, targetClientCode);
        return this.computeReadout(experiment, false, targetClientCode);
    }

    /**
     * Stops a running experiment (§6): forces the decision point now, so it can never linger {@code RUNNING}
     * — it terminates {@code SIGNIFICANT} (a mature, statistically-separated win) or {@code INCONCLUSIVE}.
     */
    @PreAuthorize(EDIT)
    public Experiment stop(ULong id, String targetClientCode) {
        Experiment experiment = this.loadForEdit(id, targetClientCode);
        return this.computeReadout(experiment, true, targetClientCode);
    }

    // =====================================================================================
    // Decide (promote winner + retire losers via J13; record causal learning to J20)
    // =====================================================================================

    /**
     * Acts on a significant readout (§5.4): PROMOTE the winner (a {@code SHIFT_BUDGET} toward its grain) and
     * RETIRE each loser (a {@code PAUSE_ENTITY}) — <b>both routed as guardrailed applies through
     * {@link ActionApplier}</b>, the two live-capable levers, never a platform side channel — then record
     * the <b>causal</b> attribute result to J20 (the winning arm's attributes reinforce the account's living
     * attribute map, so J20's map + predictor improve). Moves the experiment to {@code APPLIED}. Only a
     * {@code SIGNIFICANT} experiment can be decided; an {@code INCONCLUSIVE} one records no false learning.
     */
    @PreAuthorize(EDIT)
    public Experiment decide(ULong id, String targetClientCode) {

        Experiment experiment = this.loadForEdit(id, targetClientCode);

        ExperimentReadout readout = experiment.getReadout();
        if (experiment.getStatus() != AdzumpExperimentStatus.SIGNIFICANT || readout == null
                || StringUtil.safeIsBlank(readout.getWinner()))
            this.throwInvalidState(experiment);

        ExperimentVariant winner = this.findVariant(experiment, readout.getWinner());
        if (winner == null)
            this.throwInvalidState(experiment);

        List<ExperimentVariant> losers = new ArrayList<>();
        for (ExperimentVariant v : experiment.getVariants())
            if (v != null && !StringUtil.safeEquals(v.getCreativeId(), winner.getCreativeId()))
                losers.add(v);

        // The plan carries the live budget/currency the promote shift is sized against (re-reads the gate).
        CampaignPlan plan = this.campaignPlanService.read(experiment.getCampaignPlanId());

        // PROMOTE + RETIRE, both through the one J13 spine (autonomy + caps + kill-switch), never J8 direct.
        ActionSet decision = this.buildDecisionActionSet(experiment, plan, winner, losers);
        this.routeAndApply(experiment.getCampaignPlanId(), decision, AdzumpActionAuditTriggeredBy.SCHEDULER,
                targetClientCode);

        // Record the CAUSAL attribute result to J20: the winning arm's attributes reinforce the account map,
        // so the exploit/explore surface + predictor improve on a real counterfactual, not just observation.
        Creative winningCreative = new Creative().setId(winner.getCreativeId())
                .setAttributes(winner.getAttributes());
        this.creativeScoringService.recordCreativeAttributes(winner.getCreativeId(), winningCreative,
                targetClientCode);

        experiment.setStatus(AdzumpExperimentStatus.APPLIED);
        this.stampUpdatedBy(experiment);
        return this.experimentDao.update(experiment);
    }

    // =====================================================================================
    // Reads (tenant-scoped, no @PreAuthorize)
    // =====================================================================================

    /** Every experiment for a campaign within the caller's effective client, newest first (§6). Read. */
    public List<Experiment> listForCampaign(ULong campaignPlanId, String targetClientCode) {
        String clientCode = this.tenantScope(campaignPlanId, targetClientCode);
        return this.experimentDao.findByCampaign(clientCode, campaignPlanId);
    }

    // =====================================================================================
    // Readout math (maturity-aware)
    // =====================================================================================

    private Experiment computeReadout(Experiment experiment, boolean force, String targetClientCode) {

        ULong campaignId = experiment.getCampaignPlanId();
        int minVolume = experiment.getMinVolumePerVariant() == null ? DEFAULT_MIN_VOLUME_PER_VARIANT
                : experiment.getMinVolumePerVariant();
        List<ExperimentVariant> variants = experiment.getVariants() == null ? List.of() : experiment.getVariants();

        List<VariantOutcome> outcomes = new ArrayList<>();
        for (ExperimentVariant variant : variants) {
            // J10/J20 creative-grain measurement: the blended objective + volume + maturity for this arm.
            CreativeScore score = this.creativeScoringService.getScore(campaignId, variant.getCreativeId(),
                    targetClientCode);
            double p = clamp01(score.score() / 100.0d);
            double[] ci = ExperimentStatistics.waldInterval(p, score.volume());
            outcomes.add(new VariantOutcome()
                    .setCreativeId(variant.getCreativeId())
                    .setScore(score.score())
                    .setVolume(score.volume())
                    .setCiLow(ci[0] * 100.0d)
                    .setCiHigh(ci[1] * 100.0d)
                    .setMaturity(score.maturity())
                    .setJudgeable(score.judgeable()));
        }

        VariantOutcome leader = null;
        VariantOutcome runnerUp = null;
        for (VariantOutcome o : outcomes) {
            if (leader == null || o.getScore() > leader.getScore()) {
                runnerUp = leader;
                leader = o;
            } else if (runnerUp == null || o.getScore() > runnerUp.getScore()) {
                runnerUp = o;
            }
        }

        double pValue = 1.0d;
        if (leader != null && runnerUp != null) {
            double z = ExperimentStatistics.zTwoProportion(clamp01(leader.getScore() / 100.0d), leader.getVolume(),
                    clamp01(runnerUp.getScore() / 100.0d), runnerUp.getVolume());
            pValue = ExperimentStatistics.twoTailedPValue(z);
        }

        boolean volumeMet = !outcomes.isEmpty()
                && outcomes.stream().allMatch(o -> o.getVolume() >= minVolume);
        boolean allMature = !outcomes.isEmpty() && outcomes.stream().allMatch(VariantOutcome::isJudgeable);
        boolean statSig = leader != null && runnerUp != null && pValue < ExperimentStatistics.ALPHA;

        // The trustworthy-win flag folds in the maturity gate + the min-volume gate — a separated but
        // immature (fast-only) or thin result is NOT a win, so no false learning is ever recorded.
        boolean significant = statSig && volumeMet && allMature;

        ExperimentReadout readout = new ExperimentReadout()
                .setPerVariant(outcomes)
                .setPValue(pValue)
                .setSignificant(significant)
                .setWinner(significant ? leader.getCreativeId() : null)
                .setComputedAt(LocalDateTime.now());

        // Ends on significance OR the volume/duration cap; else keeps running (never unbounded).
        boolean durationHit = experiment.getStartedAt() != null && experiment.getMaxDurationDays() != null
                && !LocalDateTime.now().isBefore(experiment.getStartedAt()
                        .plusDays(experiment.getMaxDurationDays()));
        boolean atDecision = force || significant || volumeMet || durationHit;

        AdzumpExperimentStatus newStatus;
        if (!atDecision)
            newStatus = AdzumpExperimentStatus.RUNNING;
        else if (significant)
            newStatus = AdzumpExperimentStatus.SIGNIFICANT;
        else
            newStatus = AdzumpExperimentStatus.INCONCLUSIVE;

        experiment.setReadout(readout);
        experiment.setStatus(newStatus);
        if (newStatus == AdzumpExperimentStatus.SIGNIFICANT || newStatus == AdzumpExperimentStatus.INCONCLUSIVE)
            experiment.setEndedAt(LocalDateTime.now());

        this.stampUpdatedBy(experiment);
        return this.experimentDao.update(experiment);
    }

    // =====================================================================================
    // Action construction + routing through J13
    // =====================================================================================

    /** A ROTATE_CREATIVE per arm — the live rotation that starts the split, routed through J13. */
    private ActionSet buildRotationActionSet(Experiment experiment) {

        List<Action> actions = new ArrayList<>();
        for (ExperimentVariant variant : experiment.getVariants()) {
            AdGrainId target = grain(variant.getCreativeId());
            SignificanceVerdict verdict = SignificanceVerdict.passed(0.0d, 0.0d, "experiment_rotation");
            actions.add(new Action(
                    com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType.ROTATE_CREATIVE, target,
                    new ActionChange.CreativeRotation(variant.getCreativeId(), "experiment variant"),
                    "rotate experiment variant " + variant.getCreativeId(), 0.0d, 0.5d, verdict, Risk.LOW, true));
        }
        return new ActionSet(experiment.getCampaignPlanId(), null, LocalDateTime.now(), actions, List.of(),
                0.0d, 0.0d);
    }

    /**
     * Promote (SHIFT_BUDGET toward the winner grain) + retire (PAUSE_ENTITY per loser) — the two
     * live-capable J13 levers. The promote shift redirects the losers' allocation share of the campaign
     * daily budget toward the winner; the guardrails re-assert the caps at apply, so an experiment cannot
     * exceed the campaign's spend limits.
     */
    private ActionSet buildDecisionActionSet(Experiment experiment, CampaignPlan plan, ExperimentVariant winner,
            List<ExperimentVariant> losers) {

        Money dailyBudget = dailyBudgetOf(plan);
        double dailyAmount = dailyBudget != null && dailyBudget.getAmount() != null
                ? dailyBudget.getAmount().doubleValue()
                : 0.0d;
        String currency = dailyBudget == null ? null : dailyBudget.getCurrency();

        double loserAllocation = 0.0d;
        for (ExperimentVariant loser : losers)
            loserAllocation += loser.getAllocation() == null ? 0.0d : loser.getAllocation();
        double shiftAmount = dailyAmount * loserAllocation;

        VariantOutcome winnerOutcome = outcomeFor(experiment, winner.getCreativeId());
        VariantOutcome runnerUp = bestNonWinnerOutcome(experiment, winner.getCreativeId());
        double lift = winnerOutcome != null && runnerUp != null
                ? winnerOutcome.getScore() - runnerUp.getScore()
                : 0.0d;
        double pValue = experiment.getReadout() != null && experiment.getReadout().getPValue() != null
                ? experiment.getReadout().getPValue()
                : 0.0d;
        double confidence = clamp01(1.0d - pValue);
        long sample = winnerOutcome == null ? 0L : winnerOutcome.getVolume();

        AdGrainId winnerGrain = grain(winner.getCreativeId());
        AdGrainId sourceGrain = losers.isEmpty() ? winnerGrain : grain(losers.get(0).getCreativeId());

        List<Action> actions = new ArrayList<>();

        // PROMOTE the winner — a SHIFT_BUDGET (a live-capable lever).
        actions.add(new Action(
                com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType.SHIFT_BUDGET, winnerGrain,
                new ActionChange.BudgetShift(sourceGrain, winnerGrain,
                        new Money(BigDecimal.valueOf(shiftAmount), currency), clamp01(loserAllocation)),
                "promote experiment winner " + winner.getCreativeId(), lift, confidence,
                SignificanceVerdict.passed(sample, confidence, "experiment_winner"), Risk.MED, true));

        // RETIRE each loser — a PAUSE_ENTITY (the other live-capable lever). kill=false: a controlled
        // retire of an experiment arm, not a converter kill.
        for (ExperimentVariant loser : losers) {
            VariantOutcome loserOutcome = outcomeFor(experiment, loser.getCreativeId());
            long loserSample = loserOutcome == null ? 0L : loserOutcome.getVolume();
            actions.add(new Action(
                    com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType.PAUSE_ENTITY,
                    grain(loser.getCreativeId()),
                    new ActionChange.Pause(false, "retire experiment loser " + loser.getCreativeId()),
                    "retire experiment loser " + loser.getCreativeId(), -lift, confidence,
                    SignificanceVerdict.passed(loserSample, confidence, "experiment_loser"), Risk.MED, true));
        }

        double objectiveBefore = runnerUp == null ? 0.0d : runnerUp.getScore();
        double objectiveAfter = winnerOutcome == null ? 0.0d : winnerOutcome.getScore();
        return new ActionSet(experiment.getCampaignPlanId(), null, LocalDateTime.now(), actions, List.of(),
                objectiveBefore, objectiveAfter);
    }

    /**
     * Routes an ActionSet under the campaign's effective autonomy and applies it through the one J13 spine
     * — the same route-then-apply the headless {@code applyLatest} path uses, so the experiment's live
     * changes are bound by the AutonomyConfig caps + guardrails + the kill-switch.
     */
    private ApplyPlan routeAndApply(ULong campaignPlanId, ActionSet actions,
            AdzumpActionAuditTriggeredBy triggeredBy, String targetClientCode) {

        AutonomyPolicy policy = AutonomyPolicy.from(
                this.autonomyConfigService.getEffective(campaignPlanId, targetClientCode));
        ApplyPlan plan = this.autonomyRouter.route(actions, policy);
        this.actionApplier.apply(campaignPlanId, plan, triggeredBy, targetClientCode);
        return plan;
    }

    // =====================================================================================
    // Attribute isolation + allocation
    // =====================================================================================

    /**
     * Rejects any variant set that is not attribute-isolated (§5.2): there must be at least two arms, they
     * must all carry the same non-empty attribute axes, and <b>exactly one</b> axis may differ across them
     * (all others held constant). Returns the single differing axis (the axis under test).
     */
    private String validateAttributeIsolation(List<ExperimentVariant> variants) {

        if (variants.size() < 2)
            this.rejectIsolation("at least two variants are required for a controlled test");

        ExperimentVariant first = variants.get(0);
        if (first.getAttributes() == null || first.getAttributes().isEmpty())
            this.rejectIsolation("each variant must carry attribute tags");

        Set<String> axes = new LinkedHashSet<>(first.getAttributes().keySet());
        for (ExperimentVariant variant : variants) {
            if (variant.getAttributes() == null || !variant.getAttributes().keySet().equals(axes))
                this.rejectIsolation("all variants must share the same attribute axes");
        }

        List<String> variedAxes = new ArrayList<>();
        for (String axis : axes) {
            Set<String> values = new LinkedHashSet<>();
            for (ExperimentVariant variant : variants)
                values.add(variant.getAttributes().get(axis));
            if (values.size() > 1)
                variedAxes.add(axis);
        }

        if (variedAxes.size() != 1)
            this.rejectIsolation(variedAxes.isEmpty()
                    ? "no attribute axis differs across variants (nothing under test)"
                    : "more than one attribute axis differs (confounded): " + variedAxes);

        return variedAxes.get(0);
    }

    private void rejectIsolation(String detail) {
        this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                AdzumpMessageResourceService.ATTRIBUTE_NOT_ISOLATED, detail);
    }

    /** Assigns an even allocation when any arm's allocation is missing/invalid or they do not sum to 1. */
    private void applyEvenAllocationIfNeeded(List<ExperimentVariant> variants) {

        boolean anyInvalid = false;
        double sum = 0.0d;
        for (ExperimentVariant variant : variants) {
            Double allocation = variant.getAllocation();
            if (allocation == null || allocation <= 0.0d) {
                anyInvalid = true;
                break;
            }
            sum += allocation;
        }

        if (anyInvalid || Math.abs(sum - 1.0d) > 1e-6d) {
            double even = 1.0d / variants.size();
            for (ExperimentVariant variant : variants)
                variant.setAllocation(even);
        }
    }

    // =====================================================================================
    // Tenancy + loading
    // =====================================================================================

    /**
     * Loads an experiment for a mutating verb, resolving tenancy: resolves the effective client, reads the
     * experiment, and re-runs the plan's managed-client gate + named-target assertion (so a manager
     * acting-as X may not touch client Y's experiment). Mirrors {@code ActionApplier.loadForDecision}.
     */
    private Experiment loadForEdit(ULong id, String targetClientCode) {

        ContextAuthentication ca = ca();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        if (id == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "id");

        Experiment experiment = this.experimentDao.readById(id);
        if (experiment == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    AdzumpMessageResourceService.OBJECT_NOT_FOUND, EXPERIMENT, id);

        CampaignPlan plan = this.campaignPlanService.read(experiment.getCampaignPlanId()); // managed-client gate
        this.assertPlanClient(plan, effectiveClient, targetClientCode);
        return experiment;
    }

    private String tenantScope(ULong campaignPlanId, String targetClientCode) {

        ContextAuthentication ca = ca();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        CampaignPlan plan = this.campaignPlanService.read(campaignPlanId); // PLAN_NOT_FOUND + managed-client gate
        this.assertPlanClient(plan, effectiveClient, targetClientCode);
        return plan.getClientCode();
    }

    private void assertPlanClient(CampaignPlan plan, String effectiveClient, String targetClientCode) {
        if (targetClientCode != null && !targetClientCode.isBlank()
                && !StringUtil.safeEquals(plan.getClientCode(), effectiveClient))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, PLAN);
    }

    /**
     * Resolves the effective client code, mirroring {@code CampaignService.resolveEffectiveClientCode} /
     * {@code CreativeScoringService}. Defaults to the caller's own client; a differing target is allowed
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

    // =====================================================================================
    // Small helpers
    // =====================================================================================

    private void requireStatus(Experiment experiment, AdzumpExperimentStatus expected) {
        if (experiment.getStatus() != expected)
            this.throwInvalidState(experiment);
    }

    private void throwInvalidState(Experiment experiment) {
        this.msgService.throwMessage(msg -> new GenericException(HttpStatus.CONFLICT, msg),
                AdzumpMessageResourceService.INVALID_EXPERIMENT_STATE, experiment.getId(),
                experiment.getStatus() == null ? "null" : experiment.getStatus().getLiteral());
    }

    private void stampUpdatedBy(Experiment experiment) {
        ContextAuthentication ca = ca();
        if (ca.getUser() != null)
            experiment.setUpdatedBy(ULong.valueOf(ca.getUser().getId()));
    }

    private ExperimentVariant findVariant(Experiment experiment, String creativeId) {
        if (experiment.getVariants() == null)
            return null;
        for (ExperimentVariant variant : experiment.getVariants())
            if (variant != null && StringUtil.safeEquals(variant.getCreativeId(), creativeId))
                return variant;
        return null;
    }

    private static VariantOutcome outcomeFor(Experiment experiment, String creativeId) {
        ExperimentReadout readout = experiment.getReadout();
        if (readout == null || readout.getPerVariant() == null)
            return null;
        for (VariantOutcome outcome : readout.getPerVariant())
            if (outcome != null && StringUtil.safeEquals(outcome.getCreativeId(), creativeId))
                return outcome;
        return null;
    }

    private static VariantOutcome bestNonWinnerOutcome(Experiment experiment, String winnerId) {
        ExperimentReadout readout = experiment.getReadout();
        if (readout == null || readout.getPerVariant() == null)
            return null;
        VariantOutcome best = null;
        for (VariantOutcome outcome : readout.getPerVariant()) {
            if (outcome == null || StringUtil.safeEquals(outcome.getCreativeId(), winnerId))
                continue;
            if (best == null || outcome.getScore() > best.getScore())
                best = outcome;
        }
        return best;
    }

    private static AdGrainId grain(String creativeId) {
        return new AdGrainId().setAdId(creativeId);
    }

    private static Money dailyBudgetOf(CampaignPlan plan) {
        if (plan == null || plan.getBody() == null || plan.getBody().getBudget() == null)
            return null;
        return plan.getBody().getBudget().getDailyBudget();
    }

    private static int positiveOr(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static double clamp01(double v) {
        if (v < 0.0d)
            return 0.0d;
        return Math.min(v, 1.0d);
    }

    private static ContextAuthentication ca() {
        return SecurityContextUtil.getUsersContextAuthentication();
    }
}
