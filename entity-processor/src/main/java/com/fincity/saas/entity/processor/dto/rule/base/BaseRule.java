package com.fincity.saas.entity.processor.dto.rule.base;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class BaseRule<T extends BaseRule<T>> extends BaseDto<T> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 1639822311147907386L;

    @Version
    private int version = 1;

    private ULong ruleId;
}
