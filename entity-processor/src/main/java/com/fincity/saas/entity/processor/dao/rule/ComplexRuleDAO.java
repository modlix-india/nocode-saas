package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_COMPLEX_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorComplexRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class ComplexRuleDAO extends BaseRuleDAO<EntityProcessorComplexRulesRecord, ComplexRule> {

    protected ComplexRuleDAO() {
        super(ComplexRule.class, ENTITY_PROCESSOR_COMPLEX_RULES, ENTITY_PROCESSOR_COMPLEX_RULES.ID);
    }
}
