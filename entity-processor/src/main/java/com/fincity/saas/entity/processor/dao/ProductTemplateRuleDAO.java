package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_RULES;

import org.springframework.stereotype.Component;

import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTemplateRulesRecord;

@Component
public class ProductTemplateRuleDAO extends RuleDAO<EntityProcessorProductTemplateRulesRecord, ProductTemplateRule> {

    protected ProductTemplateRuleDAO() {
        super(
                ProductTemplateRule.class,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_RULES,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_RULES.ID,
                ENTITY_PROCESSOR_PRODUCT_TEMPLATE_RULES.PRODUCT_TEMPLATE_ID);
    }
}
