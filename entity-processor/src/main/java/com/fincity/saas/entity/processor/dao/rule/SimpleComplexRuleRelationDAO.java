package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleComplexRuleRelationsRecord;
import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SimpleComplexRuleRelationDAO
        extends BaseDAO<EntityProcessorSimpleComplexRuleRelationsRecord, SimpleComplexRuleRelation> {

    private final Field<ULong> complexConditionIdField;

    protected SimpleComplexRuleRelationDAO() {
        super(
                SimpleComplexRuleRelation.class,
                ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS,
                ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS.ID);
        this.complexConditionIdField = ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS.COMPLEX_CONDITION_ID;
    }

    public Flux<SimpleComplexRuleRelation> findByComplexConditionId(ULong complexConditionId) {
        return Flux.from(dslContext
                        .selectFrom(table)
                        .where(complexConditionIdField.eq(complexConditionId))
                        .orderBy(table.field("ORDER", Integer.class).asc()))
                .map(rec -> rec.into(pojoClass));
    }

    public Mono<Integer> deleteByComplexConditionId(ULong complexConditionId) {
        return Mono.from(dslContext.delete(table).where(this.complexConditionIdField.eq(complexConditionId)));
    }
}
