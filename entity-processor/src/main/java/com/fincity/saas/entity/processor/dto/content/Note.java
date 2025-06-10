package com.fincity.saas.entity.processor.dto.content;

import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;

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
public class Note extends BaseContentDto<Note> {

    @Serial
    private static final long serialVersionUID = 4656579497586549236L;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.NOTE;
    }
}
