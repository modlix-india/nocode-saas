package com.modlix.saas.adzump.service;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dao.CampaignPlanDao;
import com.modlix.saas.adzump.dto.PlanCompleteness;
import com.modlix.saas.adzump.jooq.enums.AdzumpCampaignPlanStatus;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
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

    public CampaignPlanService(CampaignPlanDao dao, FeignAuthenticationService securityService,
            AdzumpMessageResourceService msgService) {

        this.dao = dao;
        this.securityService = securityService;
        this.msgService = msgService;
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

    // TODO(J5/J6): vertical-aware completeness. The required-slot set must come from the
    // vertical pack / product template once J5 lands; P0 derives a fixed structural set.
    // No @PreAuthorize: derived read, tenant-scoped via read() below.
    public PlanCompleteness completeness(ULong id) {

        CampaignPlan plan = this.read(id); // not-found + tenant check

        CampaignPlanBody body = plan.getBody();

        Map<String, Boolean> slots = new LinkedHashMap<>();
        slots.put("name", plan.getName() != null && !plan.getName().isBlank());
        slots.put("productId", plan.getProductId() != null && !plan.getProductId().isBlank());
        slots.put("objective", body != null && body.getObjective() != null);
        slots.put("budget", body != null && body.getBudget() != null);
        slots.put("schedule", body != null && body.getSchedule() != null);
        slots.put("adGroupsOrAssetGroups", body != null
                && ((body.getAdGroups() != null && !body.getAdGroups().isEmpty())
                        || (body.getAssetGroups() != null && !body.getAssetGroups().isEmpty())));
        slots.put("creatives", body != null && body.getCreatives() != null && !body.getCreatives().isEmpty());

        List<String> missingRequired = slots.entrySet()
                .stream()
                .filter(e -> !e.getValue())
                .map(Map.Entry::getKey)
                .toList();

        return new PlanCompleteness()
                .setComplete(missingRequired.isEmpty())
                .setMissingRequired(missingRequired)
                .setSlots(slots);
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
