package com.modlix.saas.adzump.service.campaign;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modlix.saas.adzump.dto.ValidationIssue;
import com.modlix.saas.adzump.dto.ValidationResult;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpCampaignPlanStatus;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.model.campaign.CampaignProductLink;
import com.modlix.saas.adzump.model.connection.PlatformCredential;
import com.modlix.saas.adzump.platform.AdPlatform;
import com.modlix.saas.adzump.platform.AdPlatformRegistry;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.platform.LaunchResult;
import com.modlix.saas.adzump.platform.PlatformRef;
import com.modlix.saas.adzump.platform.RunState;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.adzump.service.CampaignPlanService;
import com.modlix.saas.adzump.service.PlanValidationService;
import com.modlix.saas.adzump.service.connection.ConnectionService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * J8 — campaign lifecycle: the <b>single launch path</b> and the mutations that manage a plan after
 * it is live. Blocking / imperative (built like {@code files} on commons2-jooq + commons2-security);
 * no Reactor. It orchestrates the modules and owns no persistence of its own — J1
 * ({@link CampaignPlanService}) is the system of record, J2b ({@link AdPlatformRegistry}) is the
 * platform seam, J6 ({@link PlanValidationService}) is the gate, J7 (the platform
 * {@link AdPlatform#compiler()}) compiles the IR, J2 ({@link ConnectionService}) resolves tokens.
 *
 * <p><b>The one path</b> ({@link #launch}): J6 validate (defense-in-depth re-run) -&gt;
 * studied-product guard -&gt; fan out across the plan's platforms (compile -&gt; launchPaused, all
 * created <b>PAUSED</b> so nothing spends) -&gt; write the returned platform ids onto {@code plan.links}
 * and the resulting status. A1's {@code launch_campaign} and the loop (J13) both funnel through here,
 * so there is exactly one launch/mutation path, guardrailed once.
 *
 * <p><b>Do-no-harm partial failure.</b> Per-platform errors are isolated (a per-platform try/catch);
 * one platform failing never aborts another and never deletes a correctly-created paused campaign.
 * All-ok -&gt; {@code LIVE_PAUSED}; some fail -&gt; {@code PARTIALLY_LAUNCHED} with the ok ids persisted
 * and the error surfaced; none created -&gt; {@code FAILED}. A retry resumes only the missing platforms
 * (idempotency keyed on {@code plan.links[platform].campaignId}).
 *
 * <p><b>Tenant model.</b> Every mutating method resolves an effective client code (optional
 * {@code targetClientCode}, defaulting to the caller's own client; a differing target is allowed only
 * for the system client or a managing client administering it) — the same pattern as
 * {@code AutonomyConfigService.resolveEffectiveClientCode} — and the by-id read
 * ({@link CampaignPlanService#read}) enforces the managed-client gate on the plan row.
 */
@Service
public class CampaignService {

    private static final String EDIT = "hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')";

    private static final String CLIENT = "client";
    private static final String PLAN = "plan";

    // NON_NULL so a link fragment never carries a null sub-field into the RFC 7386 merge patch (a
    // null would DELETE a pre-set value, e.g. an adAccountId the planner set before launch, CONTRACT
    // §5). Only populated ids are written; absent platforms are preserved by the merge.
    private static final ObjectMapper MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final CampaignPlanService campaignPlanService;
    private final PlanValidationService validationService;
    private final AdPlatformRegistry registry;
    private final ConnectionService connections;
    private final AdzumpMessageResourceService msgService;
    private final FeignAuthenticationService securityService;

    // MONEY-SAFETY KILL-SWITCH (field-injected, NOT constructor-wired so the controller wiring is
    // untouched). Belt-and-suspenders on top of launch-PAUSED: live launch is refused unless this is
    // explicitly enabled, so a real ad account can never be touched by accident. See application.yml
    // (adzump.launch.live-enabled) — default false.
    @Value("${adzump.launch.live-enabled:false}")
    private boolean liveEnabled;

    public CampaignService(CampaignPlanService campaignPlanService, PlanValidationService validationService,
            AdPlatformRegistry registry, ConnectionService connections, AdzumpMessageResourceService msgService,
            FeignAuthenticationService securityService) {

        this.campaignPlanService = campaignPlanService;
        this.validationService = validationService;
        this.registry = registry;
        this.connections = connections;
        this.msgService = msgService;
        this.securityService = securityService;
    }

    // =====================================================================================
    // Launch — the single path
    // =====================================================================================

    /**
     * Launches a validated plan, PAUSED, across every platform it targets, writing the returned
     * platform ids onto {@code plan.links}. Idempotent: a platform already carrying a
     * {@code campaignId} is skipped, so a retry after a partial failure completes only the missing
     * platforms instead of duplicating.
     *
     * <p>TODO(idempotency token): a client-supplied idempotency token should additionally guard
     * against double-submit within the launch window; P1 relies on the links-based resume above.
     */
    @PreAuthorize(EDIT)
    public CampaignPlan launch(ULong planId, String targetClientCode) {

        // MONEY-SAFETY KILL-SWITCH. Refuse before any read / J6 validate / J7 compile / platform call
        // so a real ad account can never be touched by accident. Everything already launches PAUSED;
        // this flag is belt-and-suspenders. Flip adzump.launch.live-enabled=true to enable.
        if (!this.liveEnabled)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.LIVE_LAUNCH_DISABLED);

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        CampaignPlan plan = this.campaignPlanService.read(planId); // PLAN_NOT_FOUND + managed-client gate
        this.assertPlanClient(plan, effectiveClient, targetClientCode);

        // J6 HARD GATE, re-run at launch (defense in depth: the plan may have been patched since A3).
        ValidationResult validation = this.validationService.validate(planId, targetClientCode);
        if (!validation.isValid())
            throw this.notValidForLaunch(planId, validation);

        // Last gate before money can move once unpaused (CONTRACT §6.2).
        this.requireStudiedProduct(plan);

        if (!StatusMachine.canLaunchFrom(plan.getStatus()))
            throw this.illegalTransition(plan.getStatus(), "launch");

        return this.launchAllPlatforms(plan);
    }

    /**
     * Fans the launch out across {@code plan.getPlatforms()}, isolating per-platform errors, then
     * persists the collected links + resulting status in one atomic write. Everything is created
     * PAUSED by the SPI, so a partial launch never spends and a correctly-created paused campaign is
     * never rolled back because another platform failed.
     */
    private CampaignPlan launchAllPlatforms(CampaignPlan plan) {

        List<Platform> platforms = plan.getPlatforms();
        if (platforms.isEmpty())
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "platforms");

        Links existing = plan.getBody() != null ? plan.getBody().getLinks() : null;

        int total = platforms.size();
        int okCount = 0;
        List<Links> newLinks = new ArrayList<>();      // fragments from newly-created campaigns to merge
        List<String> failures = new ArrayList<>();     // "PLATFORM: reason"

        for (Platform platform : platforms) {

            // Idempotent resume: already launched on this platform -> keep it, do not re-create.
            if (alreadyLaunched(existing, platform)) {
                okCount++;
                continue;
            }

            try {
                PlatformCredential credential = this.connections.resolve(platform); // J2 token
                Token token = toToken(credential);
                AdPlatform adPlatform = this.registry.get(platform);

                // J7 compile (pure, no I/O). P1 passes a null EffectiveConfig; the real Meta/Google
                // compilers resolve it internally once J7's resolver lands.
                // TODO(J7): resolve EffectiveConfig (perf-policy / autonomy / milestone — campaign
                //   override -> account default -> vertical-pack) and pass it here.
                CompiledCampaign compiled = adPlatform.compiler().compile(plan, null);

                LaunchResult result = adPlatform.launchPaused(compiled, token); // SPI create PAUSED

                if (result.ok()) {
                    okCount++;
                    if (result.links() != null)
                        newLinks.add(result.links());
                } else {
                    failures.add(platform + ": " + result.error());
                }
            } catch (Exception e) {
                // Isolate: one platform's failure must not abort the others.
                failures.add(platform + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }

        AdzumpCampaignPlanStatus status = okCount == 0
                ? AdzumpCampaignPlanStatus.FAILED
                : (okCount == total ? AdzumpCampaignPlanStatus.LIVE_PAUSED
                        : AdzumpCampaignPlanStatus.PARTIALLY_LAUNCHED);

        JsonNode bodyPatch = buildLinksPatch(newLinks); // null when nothing new was created

        // Persist the ok ids + status atomically. Failed platforms are NOT written to links; the
        // PARTIALLY_LAUNCHED status + the surfaced error(s) drive the retry.
        // TODO(J17): surface the residual failure(s) to the user/agent (notification) —
        //   failures = the per-platform reasons collected above.
        return this.campaignPlanService.writeStatusAndBody(plan.getId(), status, bodyPatch);
    }

    // =====================================================================================
    // Lifecycle — activate / pause / archive
    // =====================================================================================

    /**
     * Moves a launched plan into {@code s} (ACTIVE / PAUSE / ARCHIVED) by calling the SPI across each
     * platform ref, then persisting the new plan status. Enforces the legal status transition.
     *
     * <p>{@code ACTIVE} is the first money-moving step. P1 requires explicit EDIT authority.
     * TODO(J13): route ACTIVE (and any budget-raising edit) through the AutonomyConfig caps /
     * guardrails and the agent-layer confirmation before it takes effect.
     */
    @PreAuthorize(EDIT)
    public void setStatus(ULong planId, RunState s, String targetClientCode) {

        if (s == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "runState");

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        CampaignPlan plan = this.campaignPlanService.read(planId);
        this.assertPlanClient(plan, effectiveClient, targetClientCode);

        AdzumpCampaignPlanStatus target = StatusMachine.toPlanStatus(s);
        if (!StatusMachine.canTransition(plan.getStatus(), target))
            throw this.illegalTransition(plan.getStatus(), target.getLiteral());

        Links links = plan.getBody() != null ? plan.getBody().getLinks() : null;

        for (Platform platform : plan.getPlatforms()) {
            PlatformRef ref = campaignRef(platform, links);
            if (ref == null)
                continue; // not launched on this platform yet; nothing to flip
            Token token = toToken(this.connections.resolve(platform));
            this.registry.get(platform).setStatus(token, ref, s);
        }

        this.campaignPlanService.writeStatusAndBody(planId, target, null);
    }

    // =====================================================================================
    // Edit-live — the shared validate -> compile -> mutate spine J13 reuses
    // =====================================================================================

    /**
     * Applies a live edit: patch the plan (J1, which bumps revision + runs the fetched-ids gate),
     * re-validate (J6), recompile (J7), then apply the change on-platform via the SPI. This is the
     * same spine as {@link #launch}, scoped to a live campaign; J13's apply layer reuses it so there
     * is one guardrailed mutation path.
     *
     * <p>P1 applies the budget lever (the daily budget from the patched plan). TODO(J7/J13): diff the
     * plan at object level to drive exactly which of {@code updateBudget} / {@code mutateTargeting} /
     * {@code upsertCreative} fire, instead of recompiling whole + applying budget.
     */
    @PreAuthorize(EDIT)
    public CampaignPlan editLive(ULong planId, JsonNode patch, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        CampaignPlan current = this.campaignPlanService.read(planId);
        this.assertPlanClient(current, effectiveClient, targetClientCode);

        // 1. patch the plan (merge into body; J1 bumps revision + runs the fetched-ids gate).
        CampaignPlan updated = this.campaignPlanService.patch(planId, patch);

        // 2. re-validate (J6): the plan changed.
        ValidationResult validation = this.validationService.validate(planId, targetClientCode);
        if (!validation.isValid())
            throw this.notValidForLaunch(planId, validation);

        // 3. recompile (J7) + apply via SPI (P1: the budget lever).
        Links links = updated.getBody() != null ? updated.getBody().getLinks() : null;
        Money dailyBudget = dailyBudget(updated);

        for (Platform platform : updated.getPlatforms()) {
            PlatformRef ref = campaignRef(platform, links);
            if (ref == null)
                continue; // not live on this platform
            Token token = toToken(this.connections.resolve(platform));
            AdPlatform adPlatform = this.registry.get(platform);

            // Recompile the (changed) plan. TODO(J7/J13): object-level diff selects the lever set;
            // P1 recompiles and applies the budget below.
            adPlatform.compiler().compile(updated, null);

            if (dailyBudget != null)
                adPlatform.updateBudget(token, ref, dailyBudget);
        }

        return updated;
    }

    // =====================================================================================
    // Attribute — adopt an already-running platform campaign
    // =====================================================================================

    /**
     * Attributes an already-running platform campaign (created outside adzump on a connected account)
     * to a studied product, so the loop can measure and manage it. Writes the campaign&lt;-&gt;product
     * link — which lives in <b>entity-processor</b> (the {@code campaignSuggestionsV2} link, written
     * via J11), not the adzump J1 store — and (P1: minimal) would pull the live structure via the SPI
     * to populate a read-only shadow.
     *
     * <p>TODO(J11 P2): persist the link via {@code IFeignEntityProcessorService.putCampaignProductLink}
     * — that entity-processor endpoint is not defined yet (see the {@code IFeignEntityProcessorService}
     * TODOs); this method builds the intended link and returns it without inventing a live EP path.
     * TODO(J9/J11): before linking, confirm {@code productId} has a studied profile in EP (CONTRACT
     * §6.2), the same guard {@link #launch} enforces.
     * TODO(J8 shadow): pull structure via {@code registry.get(platform)} discovery + insights into a
     * read-only plan shadow.
     */
    @PreAuthorize(EDIT)
    public CampaignProductLink attributeExisting(Platform platform, String externalCampaignId, String productId,
            String targetClientCode) {

        if (platform == null)
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "platform");

        if (externalCampaignId == null || externalCampaignId.isBlank())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "externalCampaignId");

        if (productId == null || productId.isBlank())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "productId");

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String effectiveClient = this.resolveEffectiveClientCode(targetClientCode, ca);

        return new CampaignProductLink()
                .setClientCode(effectiveClient)
                .setPlatform(platform)
                .setExternalCampaignId(externalCampaignId)
                .setProductId(productId);
    }

    // =====================================================================================
    // Guards + helpers
    // =====================================================================================

    /**
     * The studied-product guard (CONTRACT §6.2): a plan with no studied product cannot launch.
     * P1 uses a plan-local proxy — A2's product study deduces the {@code vertical} (which selects the
     * J5 playbook), so a plan with a {@code productId} but no {@code vertical} was never studied.
     * TODO(J9/J11): make this authoritative by reading the entity-processor product profile.
     */
    private void requireStudiedProduct(CampaignPlan plan) {

        if (plan.getProductId() == null || plan.getProductId().isBlank())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.UNPROCESSABLE_ENTITY, msg),
                    AdzumpMessageResourceService.PRODUCT_NOT_FOUND, "productId");

        if (plan.getVertical() == null || plan.getVertical().isBlank())
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.UNPROCESSABLE_ENTITY, msg),
                    AdzumpMessageResourceService.PRODUCT_NOT_FOUND, plan.getProductId());
    }

    /**
     * When {@code targetClientCode} names a specific client, the plan must belong to it (a manager
     * acting-as X may not launch client Y's plan). When it is blank the by-id {@link CampaignPlanService#read}
     * managed-client gate already applies and no extra check is needed.
     */
    private void assertPlanClient(CampaignPlan plan, String effectiveClient, String targetClientCode) {
        if (targetClientCode != null && !targetClientCode.isBlank()
                && !StringUtil.safeEquals(plan.getClientCode(), effectiveClient))
            this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, PLAN);
    }

    private GenericException notValidForLaunch(ULong planId, ValidationResult validation) {

        StringBuilder detail = new StringBuilder();
        if (validation.getIssues() != null)
            for (ValidationIssue issue : validation.getIssues()) {
                if (issue.getSeverity() != null && issue.getSeverity() != com.modlix.saas.adzump.dto.Severity.ERROR)
                    continue;
                if (!detail.isEmpty())
                    detail.append("; ");
                String where = issue.getPath() != null ? issue.getPath() : issue.getField();
                detail.append(where).append(" [").append(issue.getCode()).append("] ").append(issue.getMessage());
            }

        return new GenericException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Campaign plan " + planId + " is not valid for launch: " + detail);
    }

    private GenericException illegalTransition(AdzumpCampaignPlanStatus from, String to) {
        return new GenericException(HttpStatus.CONFLICT,
                "Illegal campaign status transition: " + (from == null ? "null" : from.getLiteral()) + " -> " + to);
    }

    /** Builds a body merge patch {@code {"links": {...}}} from the newly-created platform fragments. */
    private static JsonNode buildLinksPatch(List<Links> fragments) {

        if (fragments.isEmpty())
            return null;

        ObjectNode linksNode = MAPPER.createObjectNode();
        for (Links fragment : fragments) {
            if (fragment == null)
                continue;
            if (fragment.getGoogle() != null)
                linksNode.set("google", MAPPER.valueToTree(fragment.getGoogle()));
            if (fragment.getMeta() != null)
                linksNode.set("meta", MAPPER.valueToTree(fragment.getMeta()));
        }

        if (linksNode.isEmpty())
            return null;

        ObjectNode patch = MAPPER.createObjectNode();
        patch.set("links", linksNode);
        return patch;
    }

    private static boolean alreadyLaunched(Links links, Platform platform) {
        if (links == null)
            return false;
        return switch (platform) {
            case GOOGLE -> links.getGoogle() != null && notBlank(links.getGoogle().getCampaignId());
            case META -> links.getMeta() != null && notBlank(links.getMeta().getCampaignId());
        };
    }

    /** The campaign-level platform ref for a platform, or null when it has not been launched there. */
    private static PlatformRef campaignRef(Platform platform, Links links) {
        if (links == null)
            return null;
        String campaignId = switch (platform) {
            case GOOGLE -> links.getGoogle() != null ? links.getGoogle().getCampaignId() : null;
            case META -> links.getMeta() != null ? links.getMeta().getCampaignId() : null;
        };
        return notBlank(campaignId) ? new PlatformRef("campaign", campaignId) : null;
    }

    private static Token toToken(PlatformCredential credential) {
        Map<String, String> attributes = credential.getAttributes() == null ? Map.of() : credential.getAttributes();
        // Google needs an MCC / login-customer context alongside the token; Meta leaves it null.
        String loginCustomerId = attributes.get("loginCustomerId");
        return new Token(credential.getAccessToken(), credential.getAccountId(), loginCustomerId, attributes);
    }

    private static Money dailyBudget(CampaignPlan plan) {
        CampaignPlanBody body = plan.getBody();
        if (body == null || body.getBudget() == null)
            return null;
        return body.getBudget().getDailyBudget();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Resolves the effective client code for a mutation, mirroring
     * {@code AutonomyConfigService.resolveEffectiveClientCode} /
     * {@code FilesAccessPathService.checkAccessNGetClientCode}. Defaults to the caller's own client;
     * a differing target is allowed only for the system client or a managing client administering it,
     * otherwise FORBIDDEN.
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
