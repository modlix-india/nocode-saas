package com.modlix.saas.adzump.compile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.platform.PlatformCompiler;

/**
 * The Google {@link PlatformCompiler}: holds a {@code Map<CampaignType, TypeCompiler>} and dispatches
 * {@link #compile} on the plan's Google campaign type. P1 implements {@link CampaignType#SEARCH} (the
 * fullest-control RSA + keyword path); the remaining Google types are scaffolded with
 * {@link UnsupportedTypeCompiler} so they fail loudly until built (PMAX reads {@code assetGroups}).
 *
 * <p>
 * A {@code @Component} standalone for the P1 offline slice — J8 resolves it via
 * {@link PlatformCompilerRegistry}. TODO(J4): {@code GooglePlatform.compiler()} will return this bean
 * so the compiler reaches J8/J13 through {@code AdPlatform.compiler()} rather than the P1 lookup.
 * </p>
 */
@Component
public class GoogleCompiler implements PlatformCompiler {

    /** Google campaign types (mirrors {@code AdzumpCampaignPlanGoogleCampaignType}). */
    private static final List<CampaignType> GOOGLE_TYPES = List.of(
            CampaignType.SEARCH, CampaignType.PMAX, CampaignType.DEMAND_GEN, CampaignType.DISPLAY,
            CampaignType.VIDEO, CampaignType.APP, CampaignType.SHOPPING, CampaignType.DSA);

    private final Map<CampaignType, TypeCompiler> byType;

    public GoogleCompiler(GoogleSearchCompiler searchCompiler) {

        Map<CampaignType, TypeCompiler> map = new EnumMap<>(CampaignType.class);
        map.put(CampaignType.SEARCH, searchCompiler);
        for (CampaignType type : GOOGLE_TYPES)
            map.putIfAbsent(type, new UnsupportedTypeCompiler(Platform.GOOGLE, type));

        this.byType = map;
    }

    @Override
    public Platform platform() {
        return Platform.GOOGLE;
    }

    @Override
    public CompiledCampaign compile(CampaignPlan plan, EffectiveConfig cfg) {

        CampaignType type = plan == null || plan.getCampaignTypes() == null
                ? null
                : plan.getCampaignTypes().get(Platform.GOOGLE);

        if (type == null)
            throw new IllegalStateException("Plan does not target Google (no GOOGLE entry in campaignTypes)");

        TypeCompiler compiler = this.byType.get(type);
        if (compiler == null)
            throw new IllegalStateException("Google does not support campaign type " + type);

        return compiler.compile(plan, cfg);
    }
}
