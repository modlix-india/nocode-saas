package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
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
@IgnoreGeneration
public class Ad extends BaseUpdatableDto<Ad> {

    @Serial
    private static final long serialVersionUID = -5194738261054829317L;

    private String adId;
    private String adName;
    private ULong adsetId;
    private ULong campaignId;

    public Ad() {
        super();
        this.relationsMap.put(Fields.adsetId, EntitySeries.ADSET.getTable());
        this.relationsMap.put(Fields.campaignId, EntitySeries.CAMPAIGN.getTable());
    }

    public Ad(Ad ad) {
        super(ad);
        this.adId = ad.adId;
        this.adName = ad.adName;
        this.adsetId = ad.adsetId;
        this.campaignId = ad.campaignId;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.AD;
    }
}
