package com.fincity.saas.entity.processor.dto;

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

    public Source ofParent(SourceRequest sourceRequest) {
        return new Source()
                .setIsParent(Boolean.TRUE)
                .setName(sourceRequest.getName())
                .setDescription(sourceRequest.getDescription());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.SOURCE;
    }
}
