package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ConditionEvaluator;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.dto.rule.base.RuleConfig;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.google.gson.JsonElement;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleExecutionService {

    private final RuleService ruleService;
    private final SimpleRuleService simpleRuleService;
    private final ComplexRuleService complexRuleService;

    public RuleExecutionService(
            RuleService ruleService, SimpleRuleService simpleRuleService, ComplexRuleService complexRuleService) {
        this.ruleService = ruleService;
        this.simpleRuleService = simpleRuleService;
        this.complexRuleService = complexRuleService;
    }

    public <T extends RuleConfig<T>> Mono<Map<ULong, List<ULong>>> executeRulesAndGetUsers(
            T ruleConfig, JsonElement data) {
        if (ruleConfig == null || ruleConfig.getRules() == null || ruleConfig.getRules().isEmpty()) {
            return Mono.just(Map.of());
        }

        return this.executeRulesInternal(ruleConfig, data)
                .flatMap(matchedRules -> this.getDistributedUsersForRules(ruleConfig, matchedRules));
    }

    private <T extends RuleConfig<T>> Mono<Map<ULong, List<ULong>>> getDistributedUsersForRules(
            T ruleConfig, List<ULong> matchedRules) {
        return Flux.fromIterable(matchedRules)
                .flatMap(ruleId -> this.getUsersForRule(ruleConfig, ruleId)
                        .map(users -> Tuples.of(ruleId, users)))
                .collectMap(Tuple2::getT1, Tuple2::getT2, HashMap::new);
    }

    private <T extends RuleConfig<T>> Mono<List<ULong>> getUsersForRule(T ruleConfig, ULong ruleId) {
        Map<ULong, UserDistribution> distributions = ruleConfig.getUserDistributions();
        if (distributions == null || !distributions.containsKey(ruleId)) {
            return Mono.just(List.of());
        }

        UserDistribution distribution = distributions.get(ruleId);
        if (!distribution.isValidForType(ruleConfig.getUserDistributionType())) {
            return Mono.just(List.of());
        }

        // Here we would implement the logic to get users from profiles based on distribution
        // For now returning empty list as placeholder
        return Mono.just(List.of());
    }

    private <T extends RuleConfig<T>> Mono<List<ULong>> executeRulesInternal(T ruleConfig, JsonElement data) {
        List<ULong> matchedRules = new ArrayList<>();
        Map<Integer, ULong> orderedRules = ruleConfig.getRules();

        return Flux.fromIterable(orderedRules.entrySet())
                .sort(Map.Entry.comparingByKey())
                .flatMap(entry -> {
                    ULong ruleId = entry.getValue();
                    return this.evaluateRule(ruleId, data, ruleConfig, matchedRules);
                })
                .collectList()
                .map(results -> matchedRules);
    }

    private <T extends RuleConfig<T>> Mono<Boolean> evaluateRule(
            ULong ruleId, JsonElement data, T ruleConfig, List<ULong> matchedRules) {

        return FlatMapUtil.flatMapMono(() -> ruleService.read(ruleId), this::getConditionForRule, (rule, condition) -> {
            if (condition == null) {
                return Mono.just(false);
            }

            boolean shouldExecute = this.checkPreviousRulesCondition(ruleConfig, matchedRules);
            if (!shouldExecute) {
                return Mono.just(false);
            }

            boolean matches = ConditionEvaluator.evaluate(condition, data);
            if (matches) {
                matchedRules.add(ruleId);
                if (ruleConfig.isBreakAtFirstMatch()) {
                    return Mono.just(true);
                }
            } else if (!ruleConfig.isContinueOnNoMatch()) {
                return Mono.just(true);
            }

            return Mono.just(false);
        });
    }

    private Mono<AbstractCondition> getConditionForRule(Rule rule) {
        if (rule == null) {
            return Mono.empty();
        }

        if (rule.isSimple()) {
            return simpleRuleService.getCondition(rule.getId());
        }

        if (rule.isComplex()) {
            return complexRuleService.getCondition(rule.getId());
        }

        return Mono.empty();
    }

    private <T extends RuleConfig<T>> boolean checkPreviousRulesCondition(T ruleConfig, List<ULong> matchedRules) {
        if (ruleConfig.isExecuteOnlyIfAllPreviousMatch() && !matchedRules.isEmpty()) {
            return matchedRules.size() == ruleConfig.getRules().size() - 1;
        }

        if (ruleConfig.isExecuteOnlyIfAllPreviousNotMatch()) {
            return matchedRules.isEmpty();
        }

        return true;
    }
}
