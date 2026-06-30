package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.ConversionActionMappingRequest;
import java.io.Serial;
import java.math.BigDecimal;
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
public class ConversionActionMapping extends BaseUpdatableDto<ConversionActionMapping> {

    @Serial
    private static final long serialVersionUID = -8129465133217844912L;

    private ULong productTemplateId;
    private CampaignPlatform campaignPlatform;
    private String platformAccountId;
    private ULong triggerStageId;
    private ULong triggerStatusId;
    private String eventName;
    private String platformActionId;
    private BigDecimal defaultValue;
    private String currency;
    private String valueFieldPath;
    private String testEventCode;

    public ConversionActionMapping() {
        super();
        this.relationsMap.put(Fields.triggerStageId, EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.triggerStatusId, EntitySeries.STAGE.getTable());
        this.relationsMap.put(Fields.productTemplateId, EntitySeries.PRODUCT_TEMPLATE.getTable());
    }

    public ConversionActionMapping(ConversionActionMapping source) {
        super(source);
        this.productTemplateId = source.productTemplateId;
        this.campaignPlatform = source.campaignPlatform;
        this.platformAccountId = source.platformAccountId;
        this.triggerStageId = source.triggerStageId;
        this.triggerStatusId = source.triggerStatusId;
        this.eventName = source.eventName;
        this.platformActionId = source.platformActionId;
        this.defaultValue = source.defaultValue;
        this.currency = source.currency;
        this.valueFieldPath = source.valueFieldPath;
        this.testEventCode = source.testEventCode;
    }

    public static ConversionActionMapping of(ConversionActionMappingRequest request) {
        return new ConversionActionMapping()
                .setCampaignPlatform(request.getCampaignPlatform())
                .setPlatformAccountId(request.getPlatformAccountId())
                .setEventName(request.getEventName())
                .setPlatformActionId(request.getPlatformActionId())
                .setDefaultValue(request.getDefaultValue())
                .setCurrency(request.getCurrency())
                .setValueFieldPath(request.getValueFieldPath())
                .setTestEventCode(request.getTestEventCode());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.CONVERSION_ACTION_MAPPING;
    }
}
