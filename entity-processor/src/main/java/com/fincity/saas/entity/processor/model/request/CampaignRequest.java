package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CampaignRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -1420136147009444788L;

    private String campaignId;
    private String campaignName;
    private String campaignType;
    private CampaignPlatform campaignPlatform;
    private ULong productId;
    private String productCode;

}

