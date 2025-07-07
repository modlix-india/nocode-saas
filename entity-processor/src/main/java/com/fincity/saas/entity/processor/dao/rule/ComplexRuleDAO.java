package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_COMPLEX_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorComplexRulesRecord;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ComplexRuleDAO extends BaseRuleDAO<EntityProcessorComplexRulesRecord, ComplexRule> {

    private static final String PARENT_CONDITION_ID = "PARENT_CONDITION_ID";
    protected final Field<ULong> parentConditionField = table.field(PARENT_CONDITION_ID, ULong.class);

    protected ComplexRuleDAO() {
        super(ComplexRule.class, ENTITY_PROCESSOR_COMPLEX_RULES, ENTITY_PROCESSOR_COMPLEX_RULES.ID);
    }

    public Flux<ComplexRule> readByParentConditionId(ULong parentConditionId) {
        return Flux.from(dslContext
                        .selectFrom(table)
                        .where(this.parentConditionField.eq(parentConditionId))
                        .and(super.isActiveTrue()))
                .map(complexRuleRecord -> complexRuleRecord.into(pojoClass));
    }

    public Mono<ComplexRule> readByEntityId(ULong entityId, EntitySeries entitySeries) {
        return Mono.from(this.dslContext
                        .selectFrom(table)
                        .where(this.entitySeriesConditionWithParent(entityId, entitySeries))
                        .orderBy(ENTITY_PROCESSOR_COMPLEX_RULES.ID.desc())
                        .limit(1))
                .map(ruleRecord -> ruleRecord.into(this.pojoClass));
    }

    private List<Condition> entitySeriesConditionWithParent(ULong entityId, EntitySeries entitySeries) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(super.entitySeriesCondition(entityId, entitySeries));
        conditions.add(parentConditionField.isNull());
        return conditions;
    }
}
