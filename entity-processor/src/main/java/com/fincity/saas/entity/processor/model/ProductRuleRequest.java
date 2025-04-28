package com.fincity.saas.entity.processor.model;

import com.fincity.saas.entity.processor.model.base.Identity;
import com.fincity.saas.entity.processor.model.rule.RuleRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProductRuleRequest extends RuleRequest {

    @Serial
    private static final long serialVersionUID = 6284129267815703288L;

    private Identity productId;
}
