package com.modlix.saas.adzump.model.campaign;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The link that binds an <i>already-running</i> platform campaign (created outside adzump) to a
 * studied product, so the optimization loop can measure and manage it even though adzump did not
 * launch it (J8 §5.6, {@code attributeExisting}).
 *
 * <p><b>Persistence note.</b> The authoritative link entity lives in <b>entity-processor</b> (the
 * {@code campaignSuggestionsV2} link, written via J11), <b>not</b> in the adzump J1 store. This
 * class is adzump's transient view of that link — the value the {@code attributeExisting} tool
 * returns and, once the entity-processor endpoint is defined (J11 P2), the shape written there.
 */
@Data
@Accessors(chain = true)
public class CampaignProductLink implements Serializable {

    @Serial
    private static final long serialVersionUID = -6501927384650192731L;

    private String clientCode;
    private Platform platform;
    private String externalCampaignId;
    private String productId;
}
