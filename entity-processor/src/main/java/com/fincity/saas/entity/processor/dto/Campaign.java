package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import java.io.Serial;
import java.util.List;
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
@IgnoreGeneration
public class Campaign extends BaseUpdatableDto<Campaign> {

    @Serial
    private static final long serialVersionUID = -6274489567525261523L;

    private String campaignId;
    private String campaignName;
    private String campaignType;
    private CampaignPlatform campaignPlatform;
    private String platformAccountId;
    private String platformLoginId;
    private String platformDatasetId;

    /**
     * DEPRECATED single-product FK (column {@code PRODUCT_ID}). Holds the
     * "primary" product only; superseded by the many-to-many
     * {@code entity_processor_campaign_products} join. New code reads
     * {@link #productIds}.
     */
    private ULong productId;

    /**
     * Linked product ids from the join table. Not a persisted column — populated
     * on demand (e.g. by the products endpoint or report hydration); null when
     * not hydrated.
     */
    private transient List<ULong> productIds;

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
        this.platformAccountId = campaign.platformAccountId;
        this.platformLoginId = campaign.platformLoginId;
        this.platformDatasetId = campaign.platformDatasetId;
        this.productId = campaign.productId;
        this.productIds = campaign.productIds == null ? null : new java.util.ArrayList<>(campaign.productIds);
    }

    public static Campaign of(CampaignRequest campaignRequest) {
        return new Campaign()
                .setCampaignId(campaignRequest.getCampaignId())
                .setCampaignName(campaignRequest.getCampaignName())
                .setCampaignType(campaignRequest.getCampaignType())
                .setCampaignPlatform(campaignRequest.getCampaignPlatform())
                .setPlatformAccountId(campaignRequest.getPlatformAccountId())
                .setPlatformLoginId(campaignRequest.getPlatformLoginId())
                .setPlatformDatasetId(campaignRequest.getPlatformDatasetId());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.CAMPAIGN;
    }
}
