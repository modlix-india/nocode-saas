package com.modlix.saas.adzump.service.integration;

import java.util.List;

/**
 * The result of diffing the live EP integration shape against the stored per-template
 * {@code MilestoneMapping}s (J22 §5.3). Drives the app "setup needed" badge and the wizard's
 * incremental re-prompt: {@code added} needs mapping, {@code removed} names mappings to retire, and
 * {@code unmappedTemplates} are templates never set up.
 *
 * @param drifted           true when anything changed (structural change or fingerprint mismatch)
 * @param added             stages/statuses/products present live but not covered by a stored mapping
 * @param removed           keys/products covered by a stored mapping but no longer present live
 * @param unmappedTemplates template ids with no stored account-default mapping at all
 */
public record DriftReport(boolean drifted, List<Change> added, List<Change> removed,
        List<String> unmappedTemplates) {
}
