package com.fincity.saas.entity.processor.model.common;

import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class RuleResult {

    private ULong userId;
    private ULong ruleId;
    private Integer ruleOrder;
    private DistributionType distributionType;
    private ULong productId;
    private ULong productTemplateId;
    private ULong stageId;
}
