package com.modlix.saas.adzump.model.campaign;

import java.io.Serial;
import java.io.Serializable;

import com.modlix.saas.adzump.enums.Platform;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Request body for {@code POST /api/adzump/campaigns/attribute} (J8 §5.6): attribute an existing
 * platform campaign to a studied product. The effective client is resolved server-side from the
 * caller's context (+ optional {@code ?clientCode} query param), never from this body.
 */
@Data
@Accessors(chain = true)
public class AttributeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 8451092736450192837L;

    private Platform platform;
    private String externalCampaignId;
    private String productId;
}
