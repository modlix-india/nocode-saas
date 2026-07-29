package com.modlix.saas.adzump.compile;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.dto.AutonomyConfig;
import com.modlix.saas.adzump.dto.PerformancePolicy;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.service.AutonomyConfigService;
import com.modlix.saas.adzump.service.PerformancePolicyService;
import com.modlix.saas.adzump.vertical.PolicyDefaults;
import com.modlix.saas.adzump.vertical.VerticalRegistry;

/**
 * Builds the {@link EffectiveConfig} for a plan by fetching the three resolution layers and composing
 * them via {@link EffectiveConfig#of}. This is the I/O half of J7 §5.2 (the compilers themselves are
 * pure) — the account-default rows come from the policy/autonomy services and the vertical layer from
 * the J5 registry; the campaign-override layer rides on the plan itself.
 *
 * <p>
 * The compilation caller (J8) resolves the config once per platform and passes it into
 * {@code platform.compiler().compile(plan, cfg)}. No {@code @PreAuthorize}: this is a read, and the
 * policy/autonomy {@code getEffective} calls already resolve the effective client (defaulting to the
 * caller's own, cross-client allowed only for a managing/system client) — the same rule as
 * {@code files}.
 * </p>
 */
@Component
public class EffectiveConfigResolver {

    private final PerformancePolicyService performancePolicyService;
    private final AutonomyConfigService autonomyConfigService;
    private final VerticalRegistry verticalRegistry;

    public EffectiveConfigResolver(PerformancePolicyService performancePolicyService,
            AutonomyConfigService autonomyConfigService, VerticalRegistry verticalRegistry) {

        this.performancePolicyService = performancePolicyService;
        this.autonomyConfigService = autonomyConfigService;
        this.verticalRegistry = verticalRegistry;
    }

    /**
     * Resolves the effective config for a plan's campaign type on one platform.
     *
     * @param plan the validated plan (carries the client, id and override blocks).
     * @param type the campaign type for the platform being compiled (drives the vertical defaults).
     */
    public EffectiveConfig resolve(CampaignPlan plan, CampaignType type) {

        ULong campaignId = plan.getId();
        String clientCode = plan.getClientCode();

        PerformancePolicy accountPolicy = this.performancePolicyService.getEffective(campaignId, clientCode);
        AutonomyConfig accountAutonomy = this.autonomyConfigService.getEffective(campaignId, clientCode);
        PolicyDefaults verticalDefaults = this.verticalRegistry.get(plan.getVertical()).defaults(type);

        return EffectiveConfig.of(plan, accountPolicy, accountAutonomy, verticalDefaults);
    }

    /** Convenience: resolves the config for the plan's campaign type on the given platform. */
    public EffectiveConfig resolve(CampaignPlan plan, Platform platform) {

        CampaignType type = plan.getCampaignTypes() == null ? null : plan.getCampaignTypes().get(platform);
        return this.resolve(plan, type);
    }
}
