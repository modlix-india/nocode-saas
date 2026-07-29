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
 * The Meta {@link PlatformCompiler}: holds a {@code Map<CampaignType, TypeCompiler>} and dispatches
 * {@link #compile} on the plan's Meta campaign type. P1 implements {@link CampaignType#LEADS} (the
 * fullest-control lead-gen path); the remaining Meta types are scaffolded with
 * {@link UnsupportedTypeCompiler} so they fail loudly until built.
 *
 * <p>
 * A {@code @Component} standalone for the P1 offline slice — J8 resolves it via
 * {@link PlatformCompilerRegistry}. TODO(J3): {@code MetaPlatform.compiler()} will return this bean
 * so the compiler reaches J8/J13 through {@code AdPlatform.compiler()} rather than the P1 lookup.
 * </p>
 */
@Component
public class MetaCompiler implements PlatformCompiler {

    /** Meta campaign types (mirrors {@code AdzumpCampaignPlanMetaCampaignType}). */
    private static final List<CampaignType> META_TYPES = List.of(
            CampaignType.LEADS, CampaignType.SALES, CampaignType.TRAFFIC, CampaignType.AWARENESS,
            CampaignType.ENGAGEMENT, CampaignType.APP, CampaignType.ADVANTAGE_PLUS);

    private final Map<CampaignType, TypeCompiler> byType;

    public MetaCompiler(MetaLeadsCompiler leadsCompiler) {

        Map<CampaignType, TypeCompiler> map = new EnumMap<>(CampaignType.class);
        map.put(CampaignType.LEADS, leadsCompiler);
        for (CampaignType type : META_TYPES)
            map.putIfAbsent(type, new UnsupportedTypeCompiler(Platform.META, type));

        this.byType = map;
    }

    @Override
    public Platform platform() {
        return Platform.META;
    }

    @Override
    public CompiledCampaign compile(CampaignPlan plan, EffectiveConfig cfg) {

        CampaignType type = plan == null || plan.getCampaignTypes() == null
                ? null
                : plan.getCampaignTypes().get(Platform.META);

        if (type == null)
            throw new IllegalStateException("Plan does not target Meta (no META entry in campaignTypes)");

        TypeCompiler compiler = this.byType.get(type);
        if (compiler == null)
            throw new IllegalStateException("Meta does not support campaign type " + type);

        return compiler.compile(plan, cfg);
    }
}
