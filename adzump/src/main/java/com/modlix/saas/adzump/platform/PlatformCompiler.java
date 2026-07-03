package com.modlix.saas.adzump.platform;

import com.modlix.saas.adzump.compile.EffectiveConfig;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.CampaignPlan;

/**
 * The J7 seam: the pure IR-to-payload step. A platform exposes its compiler via
 * {@link AdPlatform#compiler()}; compilation is deliberately separated from {@code launchPaused} so
 * payloads can be unit-tested and snapshot-diffed without touching a live account (the legacy never
 * had this seam, so payload bugs only surfaced live).
 *
 * <p>{@code compile} is <b>pure</b>: no token, no I/O. Validation has already passed (J6) and the
 * effective config (J7) is resolved by the caller and passed in.
 */
public interface PlatformCompiler {

    Platform platform();

    /**
     * Deterministically compiles the IR into a type-tagged platform payload tree. Pure — no I/O.
     *
     * @param plan the validated campaign-plan IR.
     * @param cfg  the resolved effective config (J7).
     * @return the compiled, type-tagged campaign payload.
     */
    CompiledCampaign compile(CampaignPlan plan, EffectiveConfig cfg);
}
