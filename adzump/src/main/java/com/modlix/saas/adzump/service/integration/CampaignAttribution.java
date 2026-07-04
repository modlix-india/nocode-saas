package com.modlix.saas.adzump.service.integration;

import com.modlix.saas.adzump.enums.Platform;

/**
 * A wizard-time request to attribute an external platform campaign to a studied product. Applied via
 * J8 {@code CampaignService.attributeExisting}, which writes the campaign&lt;-&gt;product link that
 * lives in entity-processor (J11), not the adzump store.
 *
 * @param platform           the ad platform
 * @param externalCampaignId the platform's campaign id
 * @param productId          the leadzump product to attribute it to
 */
public record CampaignAttribution(Platform platform, String externalCampaignId, String productId) {
}
