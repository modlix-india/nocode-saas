package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorRulesRecord;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.model.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.base.BaseService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RuleService extends BaseService<EntityProcessorRulesRecord, Rule, RuleDAO> implements IEntitySeries {

    private static final String RULE = "rule";

    private final ComplexRuleService complexRuleService;
    private final SimpleRuleService simpleRuleService;

    public RuleService(ComplexRuleService complexRuleService, SimpleRuleService simpleRuleService) {
        this.complexRuleService = complexRuleService;
        this.simpleRuleService = simpleRuleService;
    }

    @Override
    protected String getCacheName() {
        return RULE;
    }

    @Override
    protected Mono<Rule> updatableEntity(Rule rule) {

        return super.updatableEntity(rule).flatMap(e -> {
            e.setComplex(rule.isComplex());

            if (!rule.isComplex()) e.setSimple(rule.isSimple());
            return Mono.just(e);
        });
    }

    public Mono<ProcessorResponse> create(RuleRequest ruleRequest) {

        Rule rule = Rule.of(ruleRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> {
                    rule.setAppCode(hasAccess.getT1().getT1());
                    rule.setClientCode(hasAccess.getT1().getT2());

                    if (rule.getAddedByUserId() == null)
                        rule.setAddedByUserId(hasAccess.getT1().getT3());

                    return super.create(rule);
                },
                (hasAccess, cRule) -> {
                    if (rule.isComplex() && ruleRequest.getCondition() instanceof ComplexCondition complexCondition)
                        return complexRuleService
                                .createForCondition(cRule, complexCondition)
                                .map(result -> ProcessorResponse.ofCreated(cRule.getCode(), this.getEntitySeries()));

                    if (rule.isSimple() && ruleRequest.getCondition() instanceof FilterCondition filterCondition)
                        return simpleRuleService
                                .createForCondition(cRule, filterCondition)
                                .map(result -> ProcessorResponse.ofCreated(cRule.getCode(), this.getEntitySeries()));

                    return Mono.just(ProcessorResponse.ofCreated(cRule.getCode(), this.getEntitySeries()));
                });
    }
}
