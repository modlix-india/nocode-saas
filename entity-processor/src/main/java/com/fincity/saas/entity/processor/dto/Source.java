package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.SourceRequest;
import java.io.Serial;
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
public class Source extends BaseValueDto<Source> {

    @Serial
    private static final long serialVersionUID = 8940700976809710359L;

    public static Source ofParent(SourceRequest sourceRequest) {
        return new Source()
                .setValueTemplateId(
                        ULongUtil.valueOf(sourceRequest.getValueTemplateId().getId()))
                .setIsParent(Boolean.TRUE)
                .setName(sourceRequest.getName())
                .setDescription(sourceRequest.getDescription());
    }

    public static Source ofChild(SourceRequest sourceRequest, Source... parents) {
        return new Source()
                .setValueTemplateId(
                        ULongUtil.valueOf(sourceRequest.getValueTemplateId().getId()))
                .setIsParent(Boolean.FALSE)
                .setParentLevel0(parents[0].getId())
                .setParentLevel1(parents.length > 1 ? parents[1].getParentLevel0() : null)
                .setName(sourceRequest.getName())
                .setDescription(sourceRequest.getDescription());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SOURCE;
    }
}
