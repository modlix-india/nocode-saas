package com.fincity.saas.entity.collector.dto ;

import com.fincity.saas.entity.collector.model.LeadDetails;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class EntityResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = -8181721850545218567L;

    private LeadDetails leadDetails;
    private CampaignDetails campaignDetails;
}