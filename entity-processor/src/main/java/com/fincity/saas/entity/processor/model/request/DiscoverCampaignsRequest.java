package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/** Admin asks the platform "what campaigns can I enable?" for a product. */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DiscoverCampaignsRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -3471019283649021847L;

    private Identity productId;
    private CampaignPlatform campaignPlatform;
    private String platformAccountId;
    private String platformLoginId;
}
