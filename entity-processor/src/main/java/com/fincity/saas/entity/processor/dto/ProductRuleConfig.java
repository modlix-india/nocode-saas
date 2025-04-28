package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.rule.base.RuleExecutionConfig;
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
public class ProductRuleConfig extends RuleExecutionConfig<ProductRuleConfig> {

    @Serial
    private static final long serialVersionUID = 3634716140733876197L;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_RULE_CONFIG;
    }
}
