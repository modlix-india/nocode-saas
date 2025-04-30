package com.fincity.saas.entity.processor.model.request;

import com.fincity.saas.entity.processor.model.base.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleConfigRequest;
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
public class ProductRuleRequest extends RuleConfigRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6284129267815703288L;

    private Identity productId;

    @Override
    public Identity getIdentity() {
        return this.getProductId();
    }
}
