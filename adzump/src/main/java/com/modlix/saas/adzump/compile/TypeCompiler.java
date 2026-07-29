package com.modlix.saas.adzump.compile;

import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.platform.CompiledCampaign;

/**
 * One compiler per {@code (platform, campaign-type)} pair — the deterministic IR→payload transform
 * for that shape. A {@link com.modlix.saas.adzump.platform.PlatformCompiler} (e.g. {@link MetaCompiler}
 * / {@link GoogleCompiler}) holds a {@code Map<CampaignType, TypeCompiler>} and dispatches on the
 * plan's type for that platform.
 *
 * <p>
 * {@code compile} is <b>pure</b>: it takes only the plan and the already-resolved
 * {@link EffectiveConfig} and returns a {@link CompiledCampaign} payload tree — no token, no I/O, no
 * defaults. A type the platform's {@code capabilities()} does not support never reaches here (J6
 * rejected it), so compilers do not defensively branch on unsupported types; a genuinely missing
 * required value throws rather than defaulting.
 * </p>
 */
public interface TypeCompiler {

    CompiledCampaign compile(CampaignPlan plan, EffectiveConfig cfg);
}
