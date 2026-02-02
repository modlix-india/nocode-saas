package com.fincity.saas.entity.collector.dto;

import com.fincity.saas.entity.collector.model.LeadDetails;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class EntityResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -8951461910712787362L;

    private String clientCode;
    private String appCode;
    private LeadDetails leadDetails;
    private CampaignDetails campaignDetails;
}
