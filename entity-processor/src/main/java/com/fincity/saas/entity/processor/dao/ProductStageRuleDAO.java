package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_STAGE_RULES;

import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductStageRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductStageRuleDAO extends RuleDAO<EntityProcessorProductStageRulesRecord, ProductStageRule> {

    protected ProductStageRuleDAO() {
        super(
                ProductStageRule.class,
                ENTITY_PROCESSOR_PRODUCT_STAGE_RULES,
                ENTITY_PROCESSOR_PRODUCT_STAGE_RULES.ID,
                ENTITY_PROCESSOR_PRODUCT_STAGE_RULES.PRODUCT_ID);
    }
}
