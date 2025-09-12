package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class Campaign extends BaseUpdatableDto<Campaign> {

    @Serial
    private static final long serialVersionUID = -6274489567525261523L;

    private String campaignId;
    private String campaignName;
    private String campaignType;
    private CampaignPlatform campaignPlatform;
    private ULong productId;

    public Campaign() {
        super();
        this.relationsMap.put(Campaign.Fields.productId, EntitySeries.PRODUCT.getTable());
    }

    public Campaign(Campaign campaign) {
        super(campaign);
        this.campaignId = campaign.campaignId;
        this.campaignName = campaign.campaignName;
        this.campaignType = campaign.campaignType;
        this.campaignPlatform = campaign.campaignPlatform;
        this.productId = campaign.productId;
    }

    public static Campaign of(CampaignRequest campaignRequest) {
        return new Campaign()
                .setCampaignId(campaignRequest.getCampaignId())
                .setCampaignName(campaignRequest.getCampaignName())
                .setCampaignType(campaignRequest.getCampaignType())
                .setCampaignPlatform(campaignRequest.getCampaignPlatform());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.CAMPAIGN;
    }
}
