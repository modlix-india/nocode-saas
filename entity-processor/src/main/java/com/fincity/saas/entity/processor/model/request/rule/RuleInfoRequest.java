package com.fincity.saas.entity.processor.model.request.rule;

import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.base.BaseRequest;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RuleInfoRequest extends BaseRequest<RuleInfoRequest> {

    private Identity id;
    private Identity stageId;
    private boolean isDefault = false;
    private boolean breakAtFirstMatch = false;
    private DistributionType userDistributionType;
    private UserDistribution userDistribution;
}
