package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_RULES;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorRulesRecord;
import org.springframework.stereotype.Component;

@Component
public class RuleDAO extends BaseDAO<EntityProcessorRulesRecord, Rule> {

    protected RuleDAO() {
        super(Rule.class, ENTITY_PROCESSOR_RULES, ENTITY_PROCESSOR_RULES.ID);
    }
}
