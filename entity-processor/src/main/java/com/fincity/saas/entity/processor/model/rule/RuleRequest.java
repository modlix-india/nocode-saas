package com.fincity.saas.entity.processor.model.rule;

import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class RuleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 7191923870859289798L;

    private String name;
    private String description;
    private ComplexCondition complexCondition;
    private FilterCondition filterCondition;
}
