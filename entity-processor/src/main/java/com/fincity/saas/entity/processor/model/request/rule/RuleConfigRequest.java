package com.fincity.saas.entity.processor.model.request.rule;

import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Identity;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class RuleConfigRequest extends BaseRequest<RuleConfigRequest> implements Serializable {

    @Serial
    private static final long serialVersionUID = 5784534925780897041L;

    private Identity stageId;
    private Identity ruleConfigId;
    private boolean breakAtFirstMatch = false;
    private boolean executeOnlyIfAllPreviousMatch = false;
    private boolean executeOnlyIfAllPreviousNotMatch = false;
    private boolean continueOnNoMatch = true;

    private Map<Integer, RuleRequest> rules;
    private DistributionType userDistributionType;

    public abstract Identity getIdentity();
}
