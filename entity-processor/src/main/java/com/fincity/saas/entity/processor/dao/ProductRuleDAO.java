package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.RuleConfigDAO;
import com.fincity.saas.entity.processor.dto.ProductRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductRuleDAO extends RuleConfigDAO<EntityProcessorProductRulesRecord, ProductRule> {

    protected ProductRuleDAO() {
        super(
                ProductRule.class,
                ENTITY_PROCESSOR_PRODUCT_RULES,
                ENTITY_PROCESSOR_PRODUCT_RULES.ID,
                ENTITY_PROCESSOR_PRODUCT_RULES.PRODUCT_ID);
    }
}
