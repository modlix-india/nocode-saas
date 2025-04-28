package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.rule.ComplexRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorComplexRulesRecord;
import com.fincity.saas.entity.processor.service.rule.base.BaseRuleService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ComplexRuleService
        extends BaseRuleService<EntityProcessorComplexRulesRecord, ComplexRule, ComplexRuleDAO> {

    private static final String COMPLEX_RULE = "complexRule";

    @Override
    protected String getCacheName() {
        return COMPLEX_RULE;
    }

    @Override
    protected Mono<ComplexRule> updatableEntity(ComplexRule entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {
            e.setParentConditionId(entity.getParentConditionId());
            e.setLogicalOperator(entity.getLogicalOperator());
            return Mono.just(e);
        });
    }
}
