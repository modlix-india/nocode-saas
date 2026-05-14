package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Admin selects an external campaign from the discovery picker and asks to
 * enable it under a product. The server upserts the local Campaign row and
 * mirrors its adsets + ads from the platform.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class EnableCampaignRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5824012917358921304L;

    private Identity productId;
    private CampaignPlatform campaignPlatform;
    private String externalCampaignId;
    private String externalCampaignName;
    private String campaignType;
    private String platformAccountId;
    private String platformLoginId;
}
