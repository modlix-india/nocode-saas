package com.fincity.saas.entity.collector.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import lombok.Data;
import org.jooq.types.ULong;

import java.io.Serial;
import java.io.Serializable;

@Data
public class CampaignDetails extends AbstractUpdatableDTO<ULong, ULong> implements Serializable {

    @Serial
    private static final long serialVersionUID = 3825184939741547667L;

    private String adId;
    private String adName;
    private String campaignId;
    private String campaignName;
    private String adSetId;
    private String adSetName;
}