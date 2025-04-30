package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS;

import com.fincity.saas.entity.processor.dao.base.BaseDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleComplexRuleRelationsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class SimpleComplexRuleRelationDAO
        extends BaseDAO<EntityProcessorSimpleComplexRuleRelationsRecord, SimpleComplexRuleRelation> {

    protected SimpleComplexRuleRelationDAO() {
        super(
                SimpleComplexRuleRelation.class,
                ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS,
                ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS.ID);
    }

    public Flux<SimpleComplexRuleRelation> findByComplexConditionId(ULong complexConditionId) {
        return Flux.from(dslContext
                        .selectFrom(table)
                        .where(table.field("COMPLEX_CONDITION_ID", ULong.class).eq(complexConditionId))
                        .orderBy(table.field("ORDER", Integer.class).asc()))
                .map(record -> record.into(pojoClass));
    }
}
