package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS;
import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleRulesRecord;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SimpleRuleDAO extends BaseRuleDAO<EntityProcessorSimpleRulesRecord, SimpleRule> {

    private static final String HAS_PARENT = "HAS_PARENT";
    protected final Field<Boolean> hasParentField = table.field(HAS_PARENT, Boolean.class);

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

    public Mono<SimpleRule> readByEntityId(ULong entityId, EntitySeries entitySeries, boolean hasParent) {
        return Mono.from(this.dslContext
                        .selectFrom(table)
                        .where(this.entitySeriesConditionWithParent(entityId, entitySeries, hasParent))
                        .orderBy(ENTITY_PROCESSOR_SIMPLE_RULES.ID.desc())
                        .limit(1))
                .map(ruleRecord -> ruleRecord.into(this.pojoClass));
    }

    private List<Condition> entitySeriesConditionWithParent(
            ULong entityId, EntitySeries entitySeries, boolean hasParent) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(super.entitySeriesCondition(entityId, entitySeries));
        conditions.add(hasParentField.eq(hasParent));
        return conditions;
    }
}
