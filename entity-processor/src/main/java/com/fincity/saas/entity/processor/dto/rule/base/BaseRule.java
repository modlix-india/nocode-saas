package com.fincity.saas.entity.processor.dto.rule.base;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.google.gson.JsonPrimitive;
import java.io.Serial;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;
import org.springframework.data.annotation.Version;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class BaseRule<T extends BaseRule<T>> extends BaseUpdatableDto<T> {

    @Serial
    private static final long serialVersionUID = 1639822311147907386L;

    @Version
    private int version = 1;

    private ULong productTemplateRuleId;
    private ULong productStageRuleId;
    private boolean negate = false;

    protected BaseRule() {
        super();
        this.relationsMap.put(Fields.productTemplateRuleId, EntitySeries.PRODUCT_TEMPLATE_RULE.getTable());
        this.relationsMap.put(Fields.productStageRuleId, EntitySeries.PRODUCT_STAGE_RULE.getTable());
    }

    protected BaseRule(BaseRule<T> baseRule) {
        super(baseRule);
        this.version = baseRule.version;
        this.productTemplateRuleId = baseRule.productTemplateRuleId;
        this.productStageRuleId = baseRule.productStageRuleId;
        this.negate = baseRule.negate;
    }

    public T setProductTemplateRuleId(ULong productTemplateRuleId) {
        this.productTemplateRuleId = productTemplateRuleId;
        return (T) this;
    }

    public T setProductStageRuleId(ULong productStageRuleId) {
        this.productStageRuleId = productStageRuleId;
        return (T) this;
    }

    public T setNegate(boolean negate) {
        this.negate = negate;
        return (T) this;
    }

    public T setName() {
        return this.setName(this.getCode());
    }

    public ULong getRuleId(EntitySeries entitySeries) {
        if (entitySeries.equals(EntitySeries.PRODUCT_STAGE_RULE)) return this.getProductStageRuleId();
        return this.getProductTemplateRuleId();
    }

    @Override
    public Schema getSchema() {

        Schema schema = super.getSchema();

        Map<String, Schema> props = schema.getProperties();
        props.put(Fields.version, Schema.ofInteger(Fields.version).setMinimum(1));
        props.put(
                Fields.productTemplateRuleId,
                Schema.ofLong(Fields.productTemplateRuleId).setMinimum(1));
        props.put(
                Fields.productStageRuleId,
                Schema.ofLong(Fields.productStageRuleId).setMinimum(1));
        props.put(Fields.negate, Schema.ofBoolean(Fields.negate).setDefaultValue(new JsonPrimitive(false)));

        schema.setProperties(props);
        return schema;
    }
}
