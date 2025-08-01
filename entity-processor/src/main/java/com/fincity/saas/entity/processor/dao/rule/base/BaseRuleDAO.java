package com.fincity.saas.entity.processor.dao.rule.base;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.rule.base.BaseRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

public abstract class BaseRuleDAO<R extends UpdatableRecord<R>, D extends BaseRule<D>> extends BaseUpdatableDAO<R, D> {

    private static final String PRODUCT_STAGE_RULE_ID = "PRODUCT_STAGE_RULE_ID";
    private static final String PRODUCT_TEMPLATE_RULE_ID = "PRODUCT_TEMPLATE_RULE_ID";

    protected final Field<ULong> productStageRuleIdField;
    protected final Field<ULong> productTemplateRuleIdField;

    protected BaseRuleDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.productStageRuleIdField = flowTable.field(PRODUCT_STAGE_RULE_ID, ULong.class);
        this.productTemplateRuleIdField = flowTable.field(PRODUCT_TEMPLATE_RULE_ID, ULong.class);
    }

    protected Condition entitySeriesCondition(ULong entityId, EntitySeries entitySeries) {
        if (entitySeries.equals(EntitySeries.PRODUCT_STAGE_RULE)) return productStageRuleIdField.eq(entityId);
        return productTemplateRuleIdField.eq(entityId);
    }
}
