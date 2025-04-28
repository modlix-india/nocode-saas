package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.entity.processor.dao.rule.SimpleComplexRuleRelationDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleComplexRuleRelationsRecord;
import com.fincity.saas.entity.processor.service.rule.base.BaseRuleService;
import org.springframework.stereotype.Service;

@Service
public class SimpleComplexRuleRelationService
        extends BaseRuleService<
                EntityProcessorSimpleComplexRuleRelationsRecord,
                SimpleComplexRuleRelation,
                SimpleComplexRuleRelationDAO> {

    private static final String SIMPLE_COMPLEX_RULE_RELATION = "simpleComplexRuleRelation";

    @Override
    protected String getCacheName() {
        return SIMPLE_COMPLEX_RULE_RELATION;
    }
}
