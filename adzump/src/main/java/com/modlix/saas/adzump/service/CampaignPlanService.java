package com.modlix.saas.adzump.service;

import java.math.BigInteger;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dao.CampaignPlanDao;
import com.modlix.saas.adzump.dto.PlanCompleteness;
import com.modlix.saas.adzump.jooq.enums.AdzumpCampaignPlanStatus;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.validate.PlanValidator;
import com.modlix.saas.adzump.validate.ValidationContext;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * System-of-record service for the {@code CampaignPlan} IR. Blocking / imperative
 * (built like the {@code files} service on commons2-jooq + commons2-security); no
 * Reactor.
 *
 * <p><b>Tenant model (mirrors {@code FilesAccessPathService.checkAccessNGetClientCode}).</b>
 * Client scope is NEVER read from the entity body. Writes resolve an <i>effective</i>
 * client code: the optional {@code targetClientCode} method param, defaulting to the
 * caller's own {@code ca.getLoggedInFromClientCode()}. When the target differs from the
 * caller's own client it is allowed only for the system client, or a managing client
 * administering the target sub-client; otherwise FORBIDDEN. Reads validate the fetched
 * row's {@code clientCode} against the caller via the managed-client check.
 */
@Service
public class CampaignPlanService {

    public static final String SCHEMA_VERSION = "1.0";

    private static final String PLAN = "plan";
    private static final String CLIENT = "client";
    private static final String ID = "id";

    private final CampaignPlanDao dao;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;
    private final PlanValidator planValidator;

    public CampaignPlanService(CampaignPlanDao dao, FeignAuthenticationService securityService,
            AdzumpMessageResourceService msgService, PlanValidator planValidator) {

        this.dao = dao;
        this.securityService = securityService;
        this.msgService = msgService;
        this.planValidator = planValidator;
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')")
    public CampaignPlan create(CampaignPlan plan, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (plan.getName() == null || plan.getName().isBlank())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "name");

        if (plan.getProductId() == null || plan.getProductId().isBlank())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "productId");

        // clientCode is PINNED to the resolved effective client (a managing client's Owner or the
        // system client may target a sub-client via targetClientCode); it is NEVER read from the body.
        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca);

        plan.setClientCode(clientCode);
        plan.setStatus(AdzumpCampaignPlanStatus.DRAFT);
        plan.setRevision(1);
        plan.setSchemaVersion(SCHEMA_VERSION);

        if (ca.getUser() != null)
            plan.setCreatedBy(ULong.valueOf(ca.getUser().getId()));

        return this.dao.create(plan);
    }

    // No @PreAuthorize: any authenticated caller; the row is still tenant-scoped at runtime below.
    public CampaignPlan read(ULong id) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        CampaignPlan plan = this.findById(id);

        if (plan == null)
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                    AdzumpMessageResourceService.PLAN_NOT_FOUND, id);

        if (!this.isClientBeingManaged(ca.getLoggedInFromClientCode(), plan.getClientCode()))
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, PLAN);

        return plan;
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')")
    public CampaignPlan patch(ULong id, JsonNode mergePatch) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (mergePatch == null || !mergePatch.isObject())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "mergePatch");

        CampaignPlan plan = this.read(id); // not-found (PLAN_NOT_FOUND) + tenant (managed-client) check

        if (!this.assertIdsFetched(plan, ca))
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.IDS_NOT_FETCHED, id);

        return this.dao.applyMergePatch(id, mergePatch);
    }

    /**
     * Persists the outcome of a J8 lifecycle step: transitions the plan {@code status} and, when
     * {@code bodyPatch} is non-null, merges it into the plan body — atomically, with the DAO's
     * optimistic-revision guard (see {@link CampaignPlanDao#patchBodyAndStatus}). Used by the launch
     * fan-out to write the returned platform {@code links} together with the resulting status
     * ({@code LIVE_PAUSED} / {@code PARTIALLY_LAUNCHED} / {@code FAILED}) in one write, and by
     * {@code setStatus} for a pure status transition ({@code bodyPatch == null}).
     *
     * <p>Carries the EDIT authority (it mutates) and re-runs the by-id tenant gate via {@link #read},
     * so it is safe even though the orchestrating {@code CampaignService} already loaded the plan.
     * Unlike {@link #patch} it does NOT run the fetched-ids gate: the ids written here are
     * server-generated platform ids from a launch, not agent-supplied references.
     */
    @PreAuthorize("hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')")
    public CampaignPlan writeStatusAndBody(ULong id, AdzumpCampaignPlanStatus status, JsonNode bodyPatch) {

        if (status == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "status");

        this.read(id); // PLAN_NOT_FOUND + managed-client tenant gate

        return this.dao.patchBodyAndStatus(id, bodyPatch, status);
    }

    // Vertical- + type-aware completeness is now owned by J6: delegate to PlanValidator over a ctx built
    // from the plan (empty fetched-id set is fine, completeness is structural). This supersedes the old
    // hardcoded structural slot list. No @PreAuthorize: derived read, tenant-scoped via read() below.
    public PlanCompleteness completeness(ULong id) {

        CampaignPlan plan = this.read(id); // not-found + tenant check

        return this.planValidator.completeness(plan, ValidationContext.of(plan));
    }

    // TODO(A1 fetched-ids gate): every platform-entity id referenced by the plan (audiences,
    // pixels, pages, conversion actions, forms, ...) must have been fetched from the connected
    // ad account in this session before a patch/launch may reference it. P0 stub allows
    // everything; A1 supplies the fetched-ids session registry that makes this real.
    public boolean assertIdsFetched(CampaignPlan plan, ContextAuthentication ca) {
        return true;
    }

    /**
     * Returns the plan by id or {@code null} when absent. The commons2 base {@code readById}
     * throws a generic OBJECT_NOT_FOUND on absence; this null-returning lookup lets {@link #read}
     * surface the domain-specific PLAN_NOT_FOUND instead.
     */
    private CampaignPlan findById(ULong id) {
        List<CampaignPlan> rows = this.dao.readAll(FilterCondition.make(ID, id));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Resolves the effective client code for a write, mirroring
     * {@code FilesAccessPathService.checkAccessNGetClientCode} (the authority check there is
     * handled here by {@code @PreAuthorize}). Defaults to the caller's own client; a differing
     * target is allowed only for the system client or a managing client administering the target.
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
            return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                    AdzumpMessageResourceService.FORBIDDEN_PERMISSION, CLIENT);

        return target;
    }

    private boolean isClientBeingManaged(String managingClientCode, String clientCode) {

        if (StringUtil.safeEquals(managingClientCode, clientCode))
            return true;

        return Boolean.TRUE.equals(this.securityService.doesClientManageClientCode(managingClientCode, clientCode));
    }
}
