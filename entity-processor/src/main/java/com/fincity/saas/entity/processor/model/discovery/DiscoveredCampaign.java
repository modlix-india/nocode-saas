package com.fincity.saas.entity.processor.model.discovery;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Read-only view of a single campaign as returned by an external ad-platform API
 * (Google Ads, Meta, etc.) for the admin picker. Decoupled from the local
 * {@code Campaign} DTO so the discovery surface is not constrained by our
 * persistence shape and so we can annotate {@code enabled} based on whether a
 * local row already exists.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DiscoveredCampaign implements Serializable {

    @Serial
    private static final long serialVersionUID = 7128941053291647832L;

    private String campaignId;
    private String campaignName;
    private String campaignType;
    private String status;
    private boolean enabled;
}
