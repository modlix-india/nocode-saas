package com.fincity.saas.entity.processor.dto;

import java.io.Serial;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.ValueTemplateType;
import com.fincity.saas.entity.processor.model.request.ValueTemplateRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class ValueTemplate extends BaseDto<ValueTemplate> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 2361640922389483322L;

    private ValueTemplateType valueTemplateType;

    public static ValueTemplate of(ValueTemplateRequest valueTemplateRequest) {
        return new ValueTemplate()
                .setName(valueTemplateRequest.getName())
                .setDescription(valueTemplateRequest.getDescription())
                .setValueTemplateType(valueTemplateRequest.getValueTemplateType());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.VALUE_TEMPLATE;
    }
}
