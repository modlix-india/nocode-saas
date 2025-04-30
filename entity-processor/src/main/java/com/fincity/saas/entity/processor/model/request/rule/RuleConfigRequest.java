package com.fincity.saas.entity.processor.model.request.rule;

import com.fincity.saas.entity.processor.enums.rule.RuleType;
import com.fincity.saas.entity.processor.model.base.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public abstract class RuleConfigRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5784534925780897041L;

    private RuleType ruleType;
    private boolean breakAtFirstMatch = false;
    private boolean executeOnlyIfAllPreviousMatch = false;
    private boolean executeOnlyIfAllPreviousNotMatch = false;
    private boolean continueOnNoMatch = true;

    private Map<Integer, RuleRequest> rules;

    public abstract Identity getIdentity();
}
