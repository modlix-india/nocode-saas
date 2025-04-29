package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ProductRuleRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6284129267815703288L;

    private Identity productId;

    private Map<Integer, RuleRequest> rules;
}
