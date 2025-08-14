package com.fincity.saas.entity.collector.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
public class CampaignDetails implements Serializable {

    @Serial
    private static final long serialVersionUID = 3825184939741547667L;

    private String adId;
    private String adName;
    private String campaignId;
    private String campaignName;
    private String adSetId;
    private String adSetName;
}
