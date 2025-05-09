package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_COMPLEX_RULES;

import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorComplexRulesRecord;
import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
}
