package com.modlix.saas.adzump.service.apply;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dao.ActionAuditDao;
import com.modlix.saas.adzump.dto.ActionAudit;
import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditActionType;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditTriggeredBy;
import com.modlix.saas.adzump.jooq.enums.AdzumpActionAuditVerdict;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.snapshot.PerformanceSnapshot;
import com.modlix.saas.adzump.model.snapshot.SnapshotWindow;
import com.modlix.saas.adzump.platform.RunState;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.apply.ActionPatchTranslator.PatchPair;
import com.modlix.saas.adzump.service.campaign.CampaignService;
import com.modlix.saas.adzump.service.feedback.FeedbackService;
import com.modlix.saas.adzump.service.optimize.Action;
import com.modlix.saas.adzump.service.optimize.ActionSet;
import com.modlix.saas.adzump.service.optimize.OptimizationEngine;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J13 §5.3-5.5 — the apply layer: takes a routed {@link ApplyPlan}, re-asserts the {@link GuardrailEngine}
 * at apply time, and applies the eligible actions <b>through the one J8 mutation spine</b>
 * ({@link CampaignService#editLive} for structural levers, {@link CampaignService#setStatus} for pauses),
 * writing an {@code adzump_action_audit} row for <b>every</b> decision (applied / queued / suppressed /
 * rejected) and triggering a fresh J10 snapshot to close the loop (§5.4). Also owns the human queue verbs
 * (approve / reject) and reversal (§5.5).
 *
 * <p><b>Money-safety kill-switch (load-bearing).</b> {@code adzump.apply.live-enabled} defaults
 * <b>false</b> — mirroring {@link CampaignService}'s launch kill-switch. While it is false the applier
 * applies <b>nothing</b> live: every action the router+guardrails would apply is instead recorded
 * {@code QUEUED} with the {@code apply_live_disabled} note and <b>no call reaches the spine</b>
 * ({@code editLive}/{@code setStatus}). Reversal is likewise held. Flip it on (as the tests do via
 * {@code ReflectionTestUtils}) to let eligible actions move money.
 *
 * <p><b>Do-no-harm partial failure.</b> A single action's apply failure is isolated (downgraded to
 * {@code QUEUED} for retry) and never aborts the rest of the plan — the same fan-out isolation as the J8
 * launch path.
 *
 * <p><b>Security / tenancy.</b> Every mutating entry carries {@code EDIT} authority and resolves an
 * effective client (optional {@code targetClientCode}) the same way as {@link CampaignService} /
 * {@link FeedbackService}; the plan read re-runs the by-id managed-client gate and yields the authoritative
 * client the audit rows are scoped under. Autonomy <i>caps</i> are configured elsewhere (ADMIN-only
 * {@link AutonomyConfigService}); J13 only <b>enforces</b> them.
 */
@Service
public class ActionApplier {

    private static final Logger logger = LoggerFactory.getLogger(ActionApplier.class);

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";

    private static final String CLIENT = "client";
    private static final String PLAN = "plan";
    private static final int NOTES_MAX = 512;

    private final CampaignPlanService campaignPlanService;
    private final CampaignService campaignService;
    private final FeedbackService feedbackService;
    private final AutonomyConfigService autonomyConfigService;
    private final AutonomyRouter autonomyRouter;
    private final GuardrailEngine guardrailEngine;
    private final OptimizationEngine optimizationEngine;
    private final ActionAuditDao actionAuditDao;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    // MONEY-SAFETY KILL-SWITCH (field-injected, mirrors CampaignService.liveEnabled). Default false: the
    // autonomous apply path never touches a live ad account until this is explicitly enabled.
    @Value("${adzump.apply.live-enabled:false}")
    private boolean applyLiveEnabled;

    public ActionApplier(CampaignPlanService campaignPlanService, CampaignService campaignService,
            FeedbackService feedbackService, AutonomyConfigService autonomyConfigService,
            AutonomyRouter autonomyRouter, GuardrailEngine guardrailEngine, OptimizationEngine optimizationEngine,
            ActionAuditDao actionAuditDao, FeignAuthenticationService securityService,
            AdzumpMessageResourceService msgService) {

        this.campaignPlanService = campaignPlanService;
        this.campaignService = campaignService;
        this.feedbackService = feedbackService;
        this.autonomyConfigService = autonomyConfigService;
        this.autonomyRouter = autonomyRouter;
        this.guardrailEngine = guardrailEngine;
        this.optimizationEngine = optimizationEngine;
        this.actionAuditDao = actionAuditDao;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    // =====================================================================================
    // Apply — the guarded fan-out
    // =====================================================================================

    /**
     * Applies a routed {@link ApplyPlan}: guardrail-checks each {@link ApplyRoute#APPLY} action at apply
     * time, applies the survivors through the one mutation spine, and audits every decision. After anything
     * applies, triggers a fresh snapshot and links it to the applied rows (§5.4). Partial failure is
     * isolated per action.
     */
    @PreAuthorize(EDIT)
    public ApplyResult apply(ULong campaignId, ApplyPlan plan, AdzumpActionAuditTriggeredBy triggeredBy,
            String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca); // fail-fast tenant deny

        CampaignPlan campaign = this.campaignPlanService.read(campaignId); // PLAN_NOT_FOUND + managed-client gate
        this.assertPlanClient(campaign, effectiveClient, targetClientCode);
        String clientCode = campaign.getClientCode();

        PerformanceSnapshot snapshot = this.feedbackService.readLatest(campaignId, targetClientCode);
        AutonomyConfig autonomy = this.autonomyConfigService.getEffective(campaignId, targetClientCode);
        AutonomyPolicy policy = AutonomyPolicy.from(autonomy);

        CampaignState state = this.buildState(campaign, snapshot, policy, clientCode, campaignId);

        ULong basisSnapshotId = plan.snapshotId() != null ? plan.snapshotId()
                : (snapshot == null ? null : snapshot.getId());

        List<ApplyDecision> decisions = new ArrayList<>();
        List<ULong> appliedIds = new ArrayList<>();

        for (RoutedAction routed : plan.routed())
            decisions.add(this.processOne(routed, campaignId, clientCode, state, policy, basisSnapshotId,
                    triggeredBy, targetClientCode, appliedIds));

        ULong newSnapshotId = null;
        if (!appliedIds.isEmpty()) {
            newSnapshotId = this.reMeasure(campaignId, snapshot, targetClientCode);
            this.actionAuditDao.linkSnapshot(appliedIds, newSnapshotId);
        }

        return new ApplyResult(campaignId, List.copyOf(decisions), newSnapshotId);
    }

    /** Routes+applies the campaign's latest ActionSet under its effective autonomy (the headless path). */
    @PreAuthorize(EDIT)
    public ApplyResult applyLatest(ULong campaignId, AdzumpActionAuditTriggeredBy triggeredBy,
            String targetClientCode) {

        ActionSet actions = this.optimizationEngine.getRecommendations(campaignId, null, targetClientCode);
        AutonomyPolicy policy = AutonomyPolicy.from(this.autonomyConfigService.getEffective(campaignId,
                targetClientCode));
        ApplyPlan plan = this.autonomyRouter.route(actions, policy);
        return this.apply(campaignId, plan, triggeredBy, targetClientCode);
    }

    private ApplyDecision processOne(RoutedAction routed, ULong campaignId, String clientCode, CampaignState state,
            AutonomyPolicy policy, ULong basisSnapshotId, AdzumpActionAuditTriggeredBy triggeredBy,
            String targetClientCode, List<ULong> appliedIds) {

        Action action = routed.action();

        // Router said QUEUE / SUPPRESS -> record it, apply nothing.
        if (routed.route() == ApplyRoute.QUEUE)
            return this.writeAudit(clientCode, campaignId, action, AdzumpActionAuditVerdict.QUEUED,
                    routed.reason(), null, null, basisSnapshotId, triggeredBy);
        if (routed.route() == ApplyRoute.SUPPRESS)
            return this.writeAudit(clientCode, campaignId, action, AdzumpActionAuditVerdict.SUPPRESSED,
                    routed.reason(), null, null, basisSnapshotId, triggeredBy);

        // Router said APPLY -> re-assert guardrails against live state.
        GuardrailVerdict verdict = this.guardrailEngine.check(action, policy, state);
        if (!verdict.ok())
            return this.writeAudit(clientCode, campaignId, action, verdictFor(verdict.outcome()),
                    verdict.reason(), null, null, basisSnapshotId, triggeredBy);

        PatchPair pair = ActionPatchTranslator.translate(action, state);

        // MONEY-SAFETY KILL-SWITCH: record QUEUED with apply_live_disabled, no spine call.
        if (!this.applyLiveEnabled)
            return this.writeAudit(clientCode, campaignId, action, AdzumpActionAuditVerdict.QUEUED,
                    AdzumpMessageResourceService.APPLY_LIVE_DISABLED, pair.before(), pair.after(),
                    basisSnapshotId, triggeredBy);

        // Apply through the one spine; isolate a per-action failure (do-no-harm), never abort the rest.
        try {
            this.replayPayload(campaignId, action.type(), pair.after(), targetClientCode);
            ApplyDecision applied = this.writeAudit(clientCode, campaignId, action,
                    AdzumpActionAuditVerdict.APPLIED, routed.reason(), pair.before(), pair.after(),
                    basisSnapshotId, triggeredBy);
            if (applied.auditId() != null)
                appliedIds.add(applied.auditId());
            return applied;
        } catch (Exception e) {
            String note = "apply_failed:" + safeMessage(e);
            logger.warn("adzump.apply.failed campaign={} type={} reason={}", campaignId, action.type(),
                    safeMessage(e));
            return this.writeAudit(clientCode, campaignId, action, AdzumpActionAuditVerdict.QUEUED, note,
                    pair.before(), pair.after(), basisSnapshotId, triggeredBy);
        }
    }

    // =====================================================================================
    // Human queue verbs — approve / reject (the queue is the audit table's QUEUED rows)
    // =====================================================================================

    /**
     * Approves a queued recommendation: routes that queued action to apply (§6). Replays the queued row's
     * {@code after} payload through the one spine, records an {@code APPLIED} row (triggered by USER), and
     * re-measures. Held by the kill-switch like the autonomous path. Only a {@code QUEUED} row may be
     * approved; {@code REQUEST_VARIANT} is never applied (it belongs to the experiment path).
     */
    @PreAuthorize(EDIT)
    public ApplyDecision approve(ULong auditId, String targetClientCode) {

        ActionAudit row = this.loadForDecision(auditId, targetClientCode);

        if (row.getVerdict() != AdzumpActionAuditVerdict.QUEUED)
            throw new GenericException(HttpStatus.CONFLICT,
                    "Action audit " + auditId + " is not a queued recommendation (verdict "
                            + (row.getVerdict() == null ? "null" : row.getVerdict().getLiteral()) + ")");

        if (row.getActionType() == AdzumpActionAuditActionType.REQUEST_VARIANT)
            throw new GenericException(HttpStatus.CONFLICT,
                    "REQUEST_VARIANT is not applicable through the apply spine; route it to the experiment path");

        if (!this.applyLiveEnabled)
            return this.copyAudit(row, AdzumpActionAuditVerdict.QUEUED,
                    AdzumpMessageResourceService.APPLY_LIVE_DISABLED, row.getBeforeValue(), row.getAfterValue(),
                    AdzumpActionAuditTriggeredBy.USER);

        this.replayPayload(row.getCampaignPlanId(), row.getActionType(), row.getAfterValue(), targetClientCode);

        ApplyDecision decision = new ApplyDecision(row.getActionType(), AdzumpActionAuditVerdict.APPLIED, "approved",
                this.copyAudit(row, AdzumpActionAuditVerdict.APPLIED, "approved", row.getBeforeValue(),
                        row.getAfterValue(), AdzumpActionAuditTriggeredBy.USER).auditId());

        // Close the loop for the approved action too.
        PerformanceSnapshot basis = this.feedbackService.readLatest(row.getCampaignPlanId(), targetClientCode);
        ULong newSnapshotId = this.reMeasure(row.getCampaignPlanId(), basis, targetClientCode);
        if (decision.auditId() != null && newSnapshotId != null)
            this.actionAuditDao.linkSnapshot(List.of(decision.auditId()), newSnapshotId);

        return decision;
    }

    /** Rejects a queued recommendation: records a {@code REJECTED} row, applies nothing (§6). */
    @PreAuthorize(EDIT)
    public ApplyDecision reject(ULong auditId, String targetClientCode) {

        ActionAudit row = this.loadForDecision(auditId, targetClientCode);

        if (row.getVerdict() != AdzumpActionAuditVerdict.QUEUED)
            throw new GenericException(HttpStatus.CONFLICT,
                    "Action audit " + auditId + " is not a queued recommendation (verdict "
                            + (row.getVerdict() == null ? "null" : row.getVerdict().getLiteral()) + ")");

        return this.copyAudit(row, AdzumpActionAuditVerdict.REJECTED, "rejected", row.getBeforeValue(),
                row.getAfterValue(), AdzumpActionAuditTriggeredBy.USER);
    }

    // =====================================================================================
    // Reversal (§5.5) — restore the prior value through the same spine
    // =====================================================================================

    /**
     * Reverses an applied action: restores its {@code before} value through the one spine and writes a
     * {@code REVERSED} row (§5.5). Reversibility is the trust story — a mistake is bounded and undoable.
     * Held by the kill-switch (a reversal is a live mutation). Only an {@code APPLIED} row may be reversed.
     */
    @PreAuthorize(EDIT)
    public ApplyDecision reverse(ULong auditId, String targetClientCode) {

        ActionAudit row = this.loadForDecision(auditId, targetClientCode);

        if (row.getVerdict() != AdzumpActionAuditVerdict.APPLIED)
            throw new GenericException(HttpStatus.CONFLICT,
                    "Only an applied action can be reversed; audit " + auditId + " is "
                            + (row.getVerdict() == null ? "null" : row.getVerdict().getLiteral()));

        // On a reversal, before/after are swapped: we reverse FROM the applied value TO the prior value.
        if (!this.applyLiveEnabled)
            return this.copyAudit(row, AdzumpActionAuditVerdict.QUEUED,
                    AdzumpMessageResourceService.APPLY_LIVE_DISABLED, row.getAfterValue(), row.getBeforeValue(),
                    AdzumpActionAuditTriggeredBy.USER);

        this.replayPayload(row.getCampaignPlanId(), row.getActionType(), row.getBeforeValue(), targetClientCode);

        return this.copyAudit(row, AdzumpActionAuditVerdict.REVERSED, "reversed", row.getAfterValue(),
                row.getBeforeValue(), AdzumpActionAuditTriggeredBy.USER);
    }

    // =====================================================================================
    // Spine replay
    // =====================================================================================

    /**
     * Replays a stored payload through the one mutation spine: {@code setStatus} for a pause, {@code editLive}
     * for a structural body patch. The single guardrailed path — no second way to touch a platform.
     */
    private void replayPayload(ULong campaignId, AdzumpActionAuditActionType type, JsonNode payload,
            String targetClientCode) {

        if (type == AdzumpActionAuditActionType.PAUSE_ENTITY) {
            RunState runState = ActionPatchTranslator.runStateOf(payload);
            if (runState != null)
                this.campaignService.setStatus(campaignId, runState, targetClientCode);
            return;
        }

        JsonNode patch = ActionPatchTranslator.patchOf(payload);
        if (patch != null)
            this.campaignService.editLive(campaignId, patch, targetClientCode);
    }

    // =====================================================================================
    // Re-measure (§5.4)
    // =====================================================================================

    private ULong reMeasure(ULong campaignId, PerformanceSnapshot basis, String targetClientCode) {
        SnapshotWindow window = basis == null ? null : basis.getWindow();
        PerformanceSnapshot fresh = this.feedbackService.getSnapshot(campaignId, window, targetClientCode);
        return fresh == null ? null : fresh.getId();
    }

    // =====================================================================================
    // Audit writing
    // =====================================================================================

    private ApplyDecision writeAudit(String clientCode, ULong campaignId, Action action,
            AdzumpActionAuditVerdict verdict, String reason, JsonNode before, JsonNode after, ULong snapshotId,
            AdzumpActionAuditTriggeredBy triggeredBy) {

        ActionAudit row = new ActionAudit()
                .setClientCode(clientCode)
                .setCampaignPlanId(campaignId)
                .setActionType(action.type())
                .setVerdict(verdict)
                .setTriggeredBy(triggeredBy)
                .setBeforeValue(before)
                .setAfterValue(after)
                .setSnapshotId(snapshotId)
                .setNotes(truncate(reason));
        row.setCreatedAt(LocalDateTime.now());

        ActionAudit saved = this.actionAuditDao.create(row);
        return new ApplyDecision(action.type(), verdict, reason, saved == null ? null : saved.getId());
    }

    /** Writes a new audit row derived from an existing one (approve/reject/reverse), preserving identity. */
    private ApplyDecision copyAudit(ActionAudit source, AdzumpActionAuditVerdict verdict, String reason,
            JsonNode before, JsonNode after, AdzumpActionAuditTriggeredBy triggeredBy) {

        ActionAudit row = new ActionAudit()
                .setClientCode(source.getClientCode())
                .setCampaignPlanId(source.getCampaignPlanId())
                .setActionType(source.getActionType())
                .setVerdict(verdict)
                .setTriggeredBy(triggeredBy)
                .setBeforeValue(before)
                .setAfterValue(after)
                .setSnapshotId(source.getSnapshotId())
                .setNotes(truncate(reason));
        row.setCreatedAt(LocalDateTime.now());

        ActionAudit saved = this.actionAuditDao.create(row);
        return new ApplyDecision(source.getActionType(), verdict, reason, saved == null ? null : saved.getId());
    }

    // =====================================================================================
    // State + guards + tenancy
    // =====================================================================================

    private CampaignState buildState(CampaignPlan plan, PerformanceSnapshot snapshot, AutonomyPolicy policy,
            String clientCode, ULong campaignId) {

        Map<String, LocalDateTime> lastApplied = Map.of();
        if (policy.minHoursBetweenChangesPerEntity() > 0L) {
            LocalDateTime since = LocalDateTime.now().minusHours(policy.minHoursBetweenChangesPerEntity());
            lastApplied = indexByEntity(this.actionAuditDao.recentApplied(clientCode, campaignId, since));
        }
        return new CampaignState(plan, snapshot, LocalDateTime.now(), lastApplied);
    }

    private static Map<String, LocalDateTime> indexByEntity(List<ActionAudit> rows) {
        Map<String, LocalDateTime> map = new HashMap<>();
        if (rows == null)
            return map;
        for (ActionAudit row : rows) {
            String key = grainKeyOf(row.getAfterValue());
            LocalDateTime at = row.getCreatedAt();
            if (key.isEmpty() || at == null)
                continue;
            map.merge(key, at, (a, b) -> a.isAfter(b) ? a : b);
        }
        return map;
    }

    private static String grainKeyOf(JsonNode payload) {
        if (payload == null)
            return "";
        JsonNode target = payload.get(ActionPatchTranslator.TARGET);
        if (target == null || !target.isObject())
            return "";
        if (target.hasNonNull("adId"))
            return "ad:" + target.get("adId").asText();
        if (target.hasNonNull("adSetId"))
            return "adset:" + target.get("adSetId").asText();
        if (target.hasNonNull("campaignId"))
            return "campaign:" + target.get("campaignId").asText();
        return "";
    }

    /**
     * Loads an audit row for a decision verb, resolving tenancy: the effective client is resolved, the row's
     * plan is read (managed-client gate), and the plan is asserted to belong to a named target client.
     */
    private ActionAudit loadForDecision(ULong auditId, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        if (auditId == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "auditId");

        ActionAudit row = this.actionAuditDao.readById(auditId);
        if (row == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    AdzumpMessageResourceService.OBJECT_NOT_FOUND, "Action audit", auditId);

        CampaignPlan plan = this.campaignPlanService.read(row.getCampaignPlanId()); // managed-client gate
        this.assertPlanClient(plan, effectiveClient, targetClientCode);
        return row;
    }

    private void assertPlanClient(CampaignPlan plan, String effectiveClient, String targetClientCode) {
        if (targetClientCode != null && !targetClientCode.isBlank()
                && !StringUtil.safeEquals(plan.getClientCode(), effectiveClient))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, PLAN);
    }

    /**
     * Resolves the effective client code, mirroring {@code CampaignService.resolveEffectiveClientCode}.
     * Defaults to the caller's own client; a differing target is allowed only for the system client or a
     * managing client administering it.
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

    private static AdzumpActionAuditVerdict verdictFor(GuardrailVerdict.Outcome outcome) {
        return switch (outcome) {
            case QUEUE -> AdzumpActionAuditVerdict.QUEUED;
            case SUPPRESS -> AdzumpActionAuditVerdict.SUPPRESSED;
            case REJECT -> AdzumpActionAuditVerdict.REJECTED;
            case OK -> AdzumpActionAuditVerdict.APPLIED; // unreachable (OK never routed here)
        };
    }

    private static String truncate(String value) {
        if (value == null)
            return null;
        return value.length() <= NOTES_MAX ? value : value.substring(0, NOTES_MAX);
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
