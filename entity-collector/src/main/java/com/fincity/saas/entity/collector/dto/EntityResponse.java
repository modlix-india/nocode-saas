package com.fincity.saas.entity.collector.dto ;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import lombok.Data;
import org.jooq.types.ULong;

import java.io.Serial;
import java.io.Serializable;

@Data
public class EntityResponse extends AbstractUpdatableDTO<ULong, ULong> implements Serializable {

    @Serial
    private static final long serialVersionUID = -8181721850545218567L;

    private LeadDetails leadDetails;
    private CampaignDetails campaignDetails;
}