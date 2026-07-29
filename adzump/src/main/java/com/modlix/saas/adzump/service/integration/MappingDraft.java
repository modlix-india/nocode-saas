package com.modlix.saas.adzump.service.integration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the wizard persists for one product template (J22 §5.1): the leadzump stage/status -&gt;
 * vertical-milestone mapping, the user-chosen junk statuses, the explicitly-ignored keys, and any
 * campaign-product attributions to write during setup.
 *
 * <p>{@code ignoredStages} extends the doc's four-field sketch so the CONTRACT §6.7 milestone-integrity
 * guard is expressible: every live stage/status must be mapped, marked junk, or explicitly ignored -
 * there is no fourth "silently dropped" bucket. A leadzump key present in the pipeline but absent from
 * all three sets makes {@code apply} reject the draft.
 *
 * @param productTemplateId the leadzump product template this mapping is for
 * @param stageToMilestone  leadzump stage/status key -&gt; vertical milestone key (J5)
 * @param junkStatuses      leadzump status keys the operator treats as junk (user-chosen)
 * @param ignoredStages     leadzump keys deliberately excluded from the funnel (neither mapped nor junk)
 * @param attributions      external campaigns to attribute to products during setup
 */
public record MappingDraft(String productTemplateId, Map<String, String> stageToMilestone,
        Set<String> junkStatuses, Set<String> ignoredStages, List<CampaignAttribution> attributions) {
}
