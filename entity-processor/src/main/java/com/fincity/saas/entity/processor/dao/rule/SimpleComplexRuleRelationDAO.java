package com.fincity.saas.entity.processor.dao.rule;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS;

import com.fincity.saas.entity.processor.dao.rule.base.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleComplexRuleRelationsRecord;
import org.springframework.stereotype.Component;

@Component
public class SimpleComplexRuleRelationDAO
        extends BaseRuleDAO<EntityProcessorSimpleComplexRuleRelationsRecord, SimpleComplexRuleRelation> {

    protected SimpleComplexRuleRelationDAO() {
        super(
                SimpleComplexRuleRelation.class,
                ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS,
                ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS.ID);
    }
}
