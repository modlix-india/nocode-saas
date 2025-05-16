package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_VALUE_TEMPLATE_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.RuleConfigDAO;
import com.fincity.saas.entity.processor.dto.ValueTemplateRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorValueTemplateRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ValueTemplateRuleDAO extends RuleConfigDAO<EntityProcessorValueTemplateRulesRecord, ValueTemplateRule> {

    protected ValueTemplateRuleDAO() {
        super(
                ValueTemplateRule.class,
                ENTITY_PROCESSOR_VALUE_TEMPLATE_RULES,
                ENTITY_PROCESSOR_VALUE_TEMPLATE_RULES.ID,
                ENTITY_PROCESSOR_VALUE_TEMPLATE_RULES.VALUE_TEMPLATE_ID);
    }
}
