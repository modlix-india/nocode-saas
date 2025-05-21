package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_VALUE_TEMPLATE_RULES;

import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplateRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ProductTemplateRuleDAO extends RuleDAO<EntityProcessorValueTemplateRulesRecord, ProductTemplateRule> {

    protected ProductTemplateRuleDAO() {
        super(
                ProductTemplateRule.class,
                ENTITY_PROCESSOR_VALUE_TEMPLATE_RULES,
                ENTITY_PROCESSOR_VALUE_TEMPLATE_RULES.ID,
                ENTITY_PROCESSOR_VALUE_TEMPLATE_RULES.VALUE_TEMPLATE_ID);
    }
}
