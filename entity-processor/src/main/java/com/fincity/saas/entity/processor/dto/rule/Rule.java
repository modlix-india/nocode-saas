package com.fincity.saas.entity.processor.dto.rule;

import java.io.Serial;

import org.springframework.data.annotation.Version;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;

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
public class Rule extends BaseDto<Rule> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 3634716140733876196L;

    @Version
    private int version = 1;

    private boolean isSimple = true;
    private boolean isComplex = false;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.RULE;
    }
}
