package com.modlix.saas.adzump.compile;

import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.platform.CompiledCampaign;

/**
 * Scaffold {@link TypeCompiler} for a {@code (platform, type)} pair not yet implemented. Registered
 * in a platform compiler's {@code byType} map so the P1 cut (Google SEARCH + Meta LEADS) ships while
 * every other type fails loudly and traceably instead of silently.
 *
 * <p>
 * TODO(J7 phased): implement the remaining type compilers — Google PMAX / DEMAND_GEN / DISPLAY /
 * VIDEO / APP / SHOPPING / DSA and Meta SALES / TRAFFIC / AWARENESS / ENGAGEMENT / APP /
 * ADVANTAGE_PLUS. PMAX / ADVANTAGE_PLUS read {@code body.assetGroups} (CONTRACT §1.6) rather than
 * {@code body.adGroups}; the rest follow the ad-group shape. Adding a type = add a {@code TypeCompiler}
 * and register it here; the SPI and J8 are unchanged.
 * </p>
 */
public final class UnsupportedTypeCompiler implements TypeCompiler {

    private final Platform platform;
    private final CampaignType type;

    public UnsupportedTypeCompiler(Platform platform, CampaignType type) {
        this.platform = platform;
        this.type = type;
    }

    @Override
    public CompiledCampaign compile(CampaignPlan plan, EffectiveConfig cfg) {
        throw new UnsupportedOperationException(
                "No TypeCompiler yet for " + this.platform + "/" + this.type
                        + " (P1 implements Google SEARCH + Meta LEADS). TODO(J7 phased): add this compiler.");
    }
}
