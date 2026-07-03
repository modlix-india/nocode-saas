package com.modlix.saas.adzump.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.dao.PerformancePolicyDao;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.jooq.enums.AdzumpPerformancePolicyScope;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.util.JsonMergePatchUtil;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.ComplexConditionOperator;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.service.FeignAuthenticationService;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;

/**
 * Account-default / per-campaign performance-policy config. Same shape as the autonomy and
 * milestone services. Every tenant-scoped method resolves an effective client code (see
 * {@link CampaignPlanService} for the rule, which mirrors {@code files}); scope is forced
 * server-side and never trusted from the request body.
 */
@Service
public class PerformancePolicyService {

    private static final String CLIENT_CODE = "clientCode";
    private static final String SCOPE = "scope";
    private static final String CAMPAIGN_ID = "campaignId";
    private static final String CLIENT = "client";

    private final PerformancePolicyDao dao;
    private final CampaignPlanService campaignPlanService;
    private final FeignAuthenticationService securityService;
    private final AdzumpMessageResourceService msgService;

    public PerformancePolicyService(PerformancePolicyDao dao, CampaignPlanService campaignPlanService,
            FeignAuthenticationService securityService, AdzumpMessageResourceService msgService) {

        this.dao = dao;
        this.campaignPlanService = campaignPlanService;
        this.securityService = securityService;
        this.msgService = msgService;
    }

    // Resolution: campaign-override -> account-default -> null.
    // TODO(J5): fall back to the vertical-pack default when neither row exists.
    // No @PreAuthorize: tenant-scoped to the effective client below.
    public PerformancePolicy getEffective(ULong campaignId, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();
        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca);

        if (campaignId != null) {
            PerformancePolicy override = this.findOne(clientCode, AdzumpPerformancePolicyScope.CAMPAIGN_OVERRIDE,
                    campaignId);
            if (override != null)
                return override;
        }

        return this.findOne(clientCode, AdzumpPerformancePolicyScope.ACCOUNT_DEFAULT, null);
    }

    @PreAuthorize("hasAuthority('Authorities.ROLE_Owner')")
    public PerformancePolicy putAccountDefault(PerformancePolicy policy, String targetClientCode) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (policy.getScope() != null && policy.getScope() != AdzumpPerformancePolicyScope.ACCOUNT_DEFAULT)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.INVALID_SCOPE, policy.getScope());

        if (policy.getBody() == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "body");

        // Effective client (a managing client's Owner or the system client may set a sub-client's
        // default); scope is forced server-side, never trusted from the request body.
        String clientCode = this.resolveEffectiveClientCode(targetClientCode, ca);

        policy.setScope(AdzumpPerformancePolicyScope.ACCOUNT_DEFAULT);
        policy.setClientCode(clientCode);
        policy.setCampaignId(null);

        PerformancePolicy existing = this.findOne(clientCode, AdzumpPerformancePolicyScope.ACCOUNT_DEFAULT, null);

        if (existing == null) {

            if (ca.getUser() != null)
                policy.setCreatedBy(ULong.valueOf(ca.getUser().getId()));

            return this.dao.create(policy);
        }

        existing.setBody(policy.getBody());
        existing.setVertical(policy.getVertical());

        if (ca.getUser() != null)
            existing.setUpdatedBy(ULong.valueOf(ca.getUser().getId()));

        return this.dao.update(existing);
    }

    @PreAuthorize("hasAnyAuthority('Authorities.Campaign_MANAGE','Authorities.ROLE_Owner')")
    public PerformancePolicy putCampaignOverride(ULong campaignId, JsonNode partialBody) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        if (campaignId == null)
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, CAMPAIGN_ID);

        if (partialBody == null || !partialBody.isObject())
            msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AdzumpMessageResourceService.FIELDS_MISSING, "body");

        // The campaign row carries the client. read() enforces PLAN_NOT_FOUND + the managed-client
        // check, so a caller who does not manage the campaign's client gets FORBIDDEN; the override
        // is then pinned to the campaign's own client.
        CampaignPlan campaign = this.campaignPlanService.read(campaignId);
        String clientCode = campaign.getClientCode();

        PerformancePolicy existing = this.findOne(clientCode, AdzumpPerformancePolicyScope.CAMPAIGN_OVERRIDE,
                campaignId);

        if (existing == null) {

            PerformancePolicy override = new PerformancePolicy()
                    .setClientCode(clientCode)
                    .setScope(AdzumpPerformancePolicyScope.CAMPAIGN_OVERRIDE)
                    .setCampaignId(campaignId)
                    .setBody(JsonMergePatchUtil.merge(null, partialBody));

            if (ca.getUser() != null)
                override.setCreatedBy(ULong.valueOf(ca.getUser().getId()));

            return this.dao.create(override);
        }

        existing.setBody(JsonMergePatchUtil.merge(existing.getBody(), partialBody));

        if (ca.getUser() != null)
            existing.setUpdatedBy(ULong.valueOf(ca.getUser().getId()));

        return this.dao.update(existing);
    }

    // Every finder condition carries the resolved effective clientCode, so cross-tenant rows can
    // never be resolved.
    private PerformancePolicy findOne(String clientCode, AdzumpPerformancePolicyScope scope, ULong campaignId) {

        List<AbstractCondition> conditions = new ArrayList<>();
        conditions.add(FilterCondition.make(CLIENT_CODE, clientCode));
        conditions.add(FilterCondition.make(SCOPE, scope.getLiteral()));
        if (campaignId != null)
            conditions.add(FilterCondition.make(CAMPAIGN_ID, campaignId));

        List<PerformancePolicy> rows = this.dao.readAll(
                new ComplexCondition().setConditions(conditions).setOperator(ComplexConditionOperator.AND));

        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Resolves the effective client code for a write/read, mirroring
     * {@code FilesAccessPathService.checkAccessNGetClientCode}. Defaults to the caller's own client;
     * a differing target is allowed only for the system client or a managing client administering it.
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
}
