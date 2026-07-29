package com.modlix.saas.adzump.service.integration;

import com.modlix.saas.adzump.enums.Platform;

/**
 * A platform campaign entity-processor already knows about (created outside adzump), optionally
 * already linked to a product. The wizard surfaces these so the operator can attribute unlinked ones
 * to a studied product during setup ({@link MappingDraft#attributions()} -&gt; J8 attributeExisting).
 *
 * @param platform        the ad platform the campaign runs on
 * @param externalId      the platform's campaign id
 * @param linkedProductId the product it is already attributed to, or {@code null} if unlinked
 */
public record ExternalCampaign(Platform platform, String externalId, String linkedProductId) {
}
