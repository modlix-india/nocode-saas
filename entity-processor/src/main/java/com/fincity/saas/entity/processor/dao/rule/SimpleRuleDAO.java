package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class SimpleRuleDAO extends BaseRuleDAO<EntityProcessorSimpleRulesRecord, SimpleRule> {

    protected SimpleRuleDAO() {
        super(SimpleRule.class, ENTITY_PROCESSOR_SIMPLE_RULES, ENTITY_PROCESSOR_SIMPLE_RULES.ID);
    }
}
