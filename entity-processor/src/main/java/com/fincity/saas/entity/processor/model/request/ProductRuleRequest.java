package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleConfigRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ProductRuleRequest extends RuleConfigRequest {

    @Serial
    private static final long serialVersionUID = 3723645750019282921L;

    private Identity productId;

    @Override
    public Identity getIdentity() {
        return this.getProductId();
    }
}
