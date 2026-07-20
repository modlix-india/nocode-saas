package com.modlix.saas.adzump.platform;

import java.util.Map;
import java.util.Set;

import com.modlix.saas.adzump.enums.CampaignType;

/**
 * Per-platform capability map: the campaign types a platform supports and, for each, its
 * {@link OptimizationProfile} (optimizable levers + finest reporting grain). J7 (compile), A4, and
 * J12 branch on this data instead of {@code instanceof} — it replaces the old scattered
 * "lead forms? RSA?" flags. A type absent from {@link #supportedTypes()} is not launchable on this
 * platform; a supported type with a thin profile (PMax / Advantage+) is still fully launchable, it
 * simply optimizes at fewer levers.
 *
 * @param profiles the supported types mapped to their optimization profiles (never null).
 */
public record PlatformCapabilities(Map<CampaignType, OptimizationProfile> profiles) {

    public PlatformCapabilities {
        profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
    }

    /** The campaign types this platform supports. */
    public Set<CampaignType> supportedTypes() {
        return this.profiles.keySet();
    }

    public boolean supports(CampaignType type) {
        return this.profiles.containsKey(type);
    }

    /** The optimization profile for a type, or {@code null} when the type is unsupported. */
    public OptimizationProfile optimizationFor(CampaignType type) {
        return this.profiles.get(type);
    }
}
