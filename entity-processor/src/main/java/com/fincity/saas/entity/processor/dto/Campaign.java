package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import java.io.Serial;
import java.util.Map;
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
        this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
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

    @Override
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.campaignId, DbSchema.ofChar(Fields.campaignId, 32));
        props.put(Fields.campaignName, DbSchema.ofChar(Fields.campaignId, 128));
        props.put(Fields.campaignType, DbSchema.ofChar(Fields.campaignId, 32));
        props.put(Fields.campaignPlatform, DbSchema.ofEnum(Fields.campaignPlatform, CampaignPlatform.class));
        props.put(Fields.productId, DbSchema.ofNumberId(Fields.productId));

        schema.setProperties(props);
    }
}
