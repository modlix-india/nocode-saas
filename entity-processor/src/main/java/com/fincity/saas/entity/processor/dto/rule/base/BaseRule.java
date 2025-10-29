package com.fincity.saas.entity.processor.dto.rule.base;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.util.DbSchema;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
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

    @SuppressWarnings("unchecked")
    public T setProductTemplateRuleId(ULong productTemplateRuleId) {
        this.productTemplateRuleId = productTemplateRuleId;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setProductStageRuleId(ULong productStageRuleId) {
        this.productStageRuleId = productStageRuleId;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
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
    public void extendSchema(Schema schema) {

        super.extendSchema(schema);

        Map<String, Schema> props = schema.getProperties();

        props.put(Fields.version, DbSchema.ofVersion(Fields.version));
        props.put(Fields.productTemplateRuleId, DbSchema.ofNumberId(Fields.productTemplateRuleId));
        props.put(Fields.productStageRuleId, DbSchema.ofNumberId(Fields.productStageRuleId));
        props.put(Fields.negate, DbSchema.ofBooleanFalse(Fields.negate));

        schema.setProperties(props);
    }
}
