package com.fincity.saas.entity.processor.model.request.rule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RuleRequest extends BaseRequest<RuleRequest> implements Serializable {

    @Serial
    private static final long serialVersionUID = 7191923870859289798L;

    private Identity ruleId;
    private AbstractCondition condition;
    private UserDistribution userDistribution;

    @JsonIgnore
    public boolean isSimple() {
        return condition instanceof FilterCondition;
    }

    @JsonIgnore
    public boolean isComplex() {
        return condition instanceof ComplexCondition;
    }
}
