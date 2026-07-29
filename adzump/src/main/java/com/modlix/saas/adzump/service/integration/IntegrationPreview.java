package com.modlix.saas.adzump.service.integration;

import java.util.List;

import com.modlix.saas.adzump.dto.MilestoneMapping;

/**
 * What {@code IntegrationWizardService.preview} returns: the live EP {@link IntegrationSnapshot}, the
 * {@link DriftReport} against the stored mappings, and the current per-template mappings, so the C
 * wizard UI can render only the gaps and changes (J22 §5.2, endpoint "snapshot + drift").
 *
 * <p>This composite is a deliberate addition to the three core J22 records: {@code preview} is
 * specified to "produce a DriftReport + the current mappings" in one EP round-trip, and folding those
 * into {@link IntegrationSnapshot} (which the CONTRACT scopes to "what EP currently exposes") would
 * muddy that shape. {@code driftCheck} returns just the {@link DriftReport} for the lightweight badge.
 *
 * @param snapshot        the live EP integration shape + fingerprint
 * @param drift           the diff vs the stored mappings
 * @param currentMappings the stored account-default mappings, per template
 */
public record IntegrationPreview(IntegrationSnapshot snapshot, DriftReport drift,
        List<MilestoneMapping> currentMappings) {
}
