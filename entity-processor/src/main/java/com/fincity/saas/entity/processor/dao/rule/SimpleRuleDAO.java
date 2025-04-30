package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleRulesRecord;
import org.jooq.DeleteQuery;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SimpleRuleDAO extends BaseRuleDAO<EntityProcessorSimpleRulesRecord, SimpleRule> {

    protected SimpleRuleDAO() {
        super(SimpleRule.class, ENTITY_PROCESSOR_SIMPLE_RULES, ENTITY_PROCESSOR_SIMPLE_RULES.ID);
    }

    public Mono<Integer> deleteByRuleId(ULong ruleId) {
        DeleteQuery<EntityProcessorSimpleRulesRecord> query = dslContext.deleteQuery(table);
        query.addConditions(table.field("RULE_ID", ULong.class).eq(ruleId));

        return Mono.from(query);
    }
}
