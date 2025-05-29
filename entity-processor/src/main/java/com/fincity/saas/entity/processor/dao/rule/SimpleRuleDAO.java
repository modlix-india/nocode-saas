package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS;
import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleRulesRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class SimpleRuleDAO extends BaseRuleDAO<EntityProcessorSimpleRulesRecord, SimpleRule> {

    protected SimpleRuleDAO() {
        super(SimpleRule.class, ENTITY_PROCESSOR_SIMPLE_RULES, ENTITY_PROCESSOR_SIMPLE_RULES.ID);
    }

    public Flux<SimpleRule> readByComplexRuleId(ULong complexRuleId) {
        return Flux.from(this.dslContext
                        .select()
                        .from(table)
                        .join(ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS)
                        .on(this.idField.eq(ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS.SIMPLE_CONDITION_ID))
                        .where(ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS.COMPLEX_CONDITION_ID.eq(complexRuleId))
                        .and(super.isActiveTrue()))
                .map(simpleRuleRecord -> simpleRuleRecord.into(pojoClass));
    }
}
