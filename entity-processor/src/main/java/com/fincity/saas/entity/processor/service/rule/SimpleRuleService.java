package com.fincity.saas.entity.processor.service.rule;

import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.rule.SimpleRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorSimpleRulesRecord;
import com.fincity.saas.entity.processor.service.rule.base.BaseRuleService;

import reactor.core.publisher.Mono;

@Service
public class SimpleRuleService extends BaseRuleService<EntityProcessorSimpleRulesRecord, SimpleRule, SimpleRuleDAO> {

    private static final String SIMPLE_RULE = "simpleRule";

    @Override
    protected String getCacheName() {
        return SIMPLE_RULE;
    }

    @Override
    protected Mono<SimpleRule> updatableEntity(SimpleRule entity) {
        return FlatMapUtil.flatMapMono(() -> super.updatableEntity(entity), e -> {
            e.setField(entity.getField());
            e.setComparisonOperator(entity.getComparisonOperator());
            e.setValue(entity.getValue());
            e.setToValue(entity.getToValue());
            e.setValueField(entity.isValueField());
            e.setToValueField(entity.isToValueField());
            e.setMatchOperator(entity.getMatchOperator());
            return Mono.just(e);
        });
    }
}
