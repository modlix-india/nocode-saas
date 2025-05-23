package com.fincity.saas.entity.processor.model.response.rule;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class RuleResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 2346565158517166763L;

    private String code;
    private String name;
    private String description;
    private AbstractCondition condition;
}
