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
public class Adset extends BaseUpdatableDto<Adset> {

    @Serial
    private static final long serialVersionUID = -7382946152839471625L;

    private String adsetId;
    private String adsetName;
    private ULong campaignId;

    public Adset() {
        super();
        this.relationsMap.put(Fields.campaignId, EntitySeries.CAMPAIGN.getTable());
    }

    public Adset(Adset adset) {
        super(adset);
        this.adsetId = adset.adsetId;
        this.adsetName = adset.adsetName;
        this.campaignId = adset.campaignId;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.ADSET;
    }
}
