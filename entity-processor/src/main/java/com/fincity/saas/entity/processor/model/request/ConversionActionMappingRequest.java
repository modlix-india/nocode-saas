package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ConversionActionMappingRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 8473520161534098123L;

    private Identity id;
    private Identity productTemplateId;
    private CampaignPlatform campaignPlatform;
    private Identity triggerStageId;
    private Identity triggerStatusId;
    private String eventName;
    private String platformActionId;
    private BigDecimal defaultValue;
    private String currency;
    private String valueFieldPath;
    private String testEventCode;
}
