package com.fincity.saas.entity.processor.dto.rule.base;

import com.fincity.saas.entity.processor.dto.base.BaseFlowDto;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.rule.RuleType;
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
public abstract class EntityRule<T extends EntityRule<T>> extends BaseFlowDto<T> implements IEntitySeries {

    @Serial
    private static final long serialVersionUID = 1844345864104376761L;

    private ULong entityId;
    private ULong ruleId;
    private RuleType ruleType;
    private int ruleOrder = 0;
}
