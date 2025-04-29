package com.fincity.saas.entity.processor.dto.rule;

import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Version;

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

    public static Rule of(RuleRequest ruleRequest) {
        return new Rule()
                .setName(ruleRequest.getName())
                .setDescription(ruleRequest.getDescription())
                .setComplex(ruleRequest.isComplex())
                .setSimple(ruleRequest.isSimple());
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.RULE;
    }
}
