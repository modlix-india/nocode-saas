package com.fincity.saas.entity.processor.dto;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import java.io.Serial;
import java.util.Map;
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
public class ProductStageRule extends Rule<ProductStageRule> {

    @Serial
    private static final long serialVersionUID = 3634716140733876197L;

    private ULong productId;

    public ProductStageRule() {
        super();
        this.relationsMap.put(Fields.productId, EntitySeries.PRODUCT.getTable());
    }

    public ProductStageRule(ProductStageRule productStageRule) {
        super(productStageRule);
        this.productId = productStageRule.productId;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.PRODUCT_STAGE_RULE;
    }

    @Override
    public ULong getEntityId() {
        return this.getProductId();
    }

    @Override
    public ProductStageRule setEntityId(ULong entityId) {
        return this.setProductId(entityId);
    }

    @Override
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.productId, DbSchema.ofNumberId(Fields.productId));

        schema.setProperties(props);
    }
}
