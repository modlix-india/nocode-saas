package com.fincity.saas.entity.processor.dto;

import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class ProductTemplateRule extends Rule<ProductTemplateRule> {

    @Serial
    private static final long serialVersionUID = 5282289027862256173L;

    private ULong productTemplateId;

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_RULE;
    }

    @Override
    public ULong getEntityId() {
        return this.getProductTemplateId();
    }

    @Override
    public ProductTemplateRule setEntityId(ULong entityId) {
        return this.setProductTemplateId(entityId);
    }

    @Override
    public ProductTemplateRule of(RuleRequest ruleRequest) {
        return new ProductTemplateRule();
    }
}
