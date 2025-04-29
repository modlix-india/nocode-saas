package com.fincity.saas.entity.processor.dto.rule.base;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.rule.RuleType;
import java.io.Serial;
import java.util.Map;
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
public abstract class RuleExecutionConfig<T extends RuleExecutionConfig<T>> extends BaseDto<T>
        implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 1844345864104376762L;

    private ULong entityId;
    private RuleType ruleType;
    private boolean breakAtFirstMatch = false;
    private boolean executeOnlyIfAllPreviousMatch = false;
    private boolean executeOnlyIfAllPreviousNotMatch = false;
    private boolean continueOnNoMatch = true;

    private Map<Integer, ULong> rules;
}
