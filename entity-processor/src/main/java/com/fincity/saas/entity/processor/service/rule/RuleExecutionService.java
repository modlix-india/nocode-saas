package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.ConditionEvaluator;
import com.fincity.saas.entity.processor.dto.rule.base.RuleConfig;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RuleExecutionService {

    private final RuleService ruleService;
    private final SimpleRuleService simpleRuleService;
    private final ComplexRuleService complexRuleService;
    private final ConditionEvaluator conditionEvaluator;
    private final Random random = new Random();

    public RuleExecutionService(
            RuleService ruleService,
            SimpleRuleService simpleRuleService,
            ComplexRuleService complexRuleService,
            ConditionEvaluator conditionEvaluator) {
        this.ruleService = ruleService;
        this.simpleRuleService = simpleRuleService;
        this.complexRuleService = complexRuleService;
        this.conditionEvaluator = conditionEvaluator;
    }

    private Mono<List<ULong>> getUsersFromProfiles(List<ULong> profileIds) {
        // TODO: Implement actual logic to fetch users from profiles
        // For now, return an empty list
        return Mono.just(List.of());
    }

    public <T extends RuleConfig<T>> Mono<List<ULong>> executeRules(T ruleConfig, JsonElement data) {
        if (ruleConfig == null
                || ruleConfig.getRules() == null
                || ruleConfig.getRules().isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(ruleConfig.getRules().entrySet())
                .sort(Map.Entry.comparingByKey())
                .flatMap(entry -> {
                    ULong ruleId = entry.getValue();
                    return this.evaluateRule(ruleId, data)
                            .filter(matches -> matches)
                            .map(matches -> ruleId);
                })
                .take(1) // Break at first match
                .collectList()
                .flatMap(matchedRules -> {
                    if (matchedRules.isEmpty()) {
                        return Mono.just(List.of());
                    }

                    ULong matchedRuleId = matchedRules.getFirst();
                    UserDistribution distribution =
                            ruleConfig.getUserDistributions().get(matchedRuleId);

                    if (distribution == null
                            || distribution.getProfileIds() == null
                            || distribution.getProfileIds().isEmpty()) {
                        return Mono.just(List.of());
                    }

                    return getUsersFromProfiles(distribution.getProfileIds())
                            .flatMap(userIds -> distributeUsers(ruleConfig, distribution, userIds));
                });
    }

    private <T extends RuleConfig<T>> Mono<List<ULong>> distributeUsers(
            T ruleConfig, UserDistribution distribution, List<ULong> userIds) {

        if (userIds == null || userIds.isEmpty()) return Mono.just(List.of());

        DistributionType type = ruleConfig.getUserDistributionType();

        if (type == null) return Mono.just(userIds);

        return switch (type) {
            case ROUND_ROBIN -> this.handleRoundRobin(ruleConfig, userIds);
            case RANDOM -> Mono.just(List.of(userIds.get(random.nextInt(userIds.size()))));
            case PERCENTAGE -> this.handlePercentage(distribution, userIds);
            case WEIGHTED -> this.handleWeighted(distribution, userIds);
            case LOAD_BALANCED -> this.handleLoadBalanced(distribution, userIds);
            case PRIORITY_QUEUE -> this.handlePriorityQueue(distribution, userIds);
            case HYBRID -> this.handleHybrid(distribution, userIds);
        };
    }

    private <T extends RuleConfig<T>> Mono<List<ULong>> handleRoundRobin(T ruleConfig, List<ULong> userIds) {

        ULong lastUsedId = ruleConfig.getLastUsedUserId();
        int currentIndex = 0;

        if (lastUsedId != null) {
            int lastIndex = userIds.indexOf(lastUsedId);
            currentIndex = (lastIndex + 1) % userIds.size();
        }

        ULong nextUserId = userIds.get(currentIndex);
        ruleConfig.setLastUsedUserId(nextUserId);

        return Mono.just(List.of(nextUserId));
    }

    private Mono<List<ULong>> handlePercentage(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getPercentage() == null || distribution.getPercentage() <= 0) return Mono.just(userIds);

        int count = (int) Math.ceil((distribution.getPercentage() / 100.0) * userIds.size());
        return Mono.just(userIds.subList(0, Math.min(count, userIds.size())));
    }

    private Mono<List<ULong>> handleWeighted(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getWeight() == null || distribution.getWeight() <= 0) return Mono.just(userIds);

        int count = Math.min(distribution.getWeight(), userIds.size());
        return Mono.just(userIds.subList(0, count));
    }

    private Mono<List<ULong>> handleLoadBalanced(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getMaxLoad() == null || distribution.getMaxLoad() <= 0) return Mono.just(userIds);

        Integer currentCount = distribution.getCurrentCount();
        if (currentCount == null) currentCount = 0;

        if (currentCount >= distribution.getMaxLoad()) return Mono.just(List.of());

        distribution.setCurrentCount(currentCount + 1);
        return Mono.just(List.of(userIds.get(currentCount % userIds.size())));
    }

    private Mono<List<ULong>> handlePriorityQueue(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getPriority() == null || distribution.getPriority() < 0) return Mono.just(userIds);

        int count = Math.min(distribution.getPriority() + 1, userIds.size());
        return Mono.just(userIds.subList(0, count));
    }

    private Mono<List<ULong>> handleHybrid(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getHybridWeights() == null
                || distribution.getHybridWeights().isEmpty()) return Mono.just(userIds);

        List<ULong> selectedUsers = new ArrayList<>();
        Map<DistributionType, Integer> weights = distribution.getHybridWeights();

        for (Map.Entry<DistributionType, Integer> entry : weights.entrySet()) {
            int weight = entry.getValue();
            if (weight <= 0) continue;

            int count = Math.min(weight, userIds.size());
            selectedUsers.addAll(userIds.subList(0, count));
        }

        return Mono.just(selectedUsers);
    }

    private Mono<Boolean> evaluateRule(ULong ruleId, JsonElement data) {
        return FlatMapUtil.flatMapMonoWithNull(
                () -> ruleService.read(ruleId),
                rule -> {
                    if (rule == null) return Mono.empty();

                    if (rule.isSimple()) return simpleRuleService.getCondition(rule.getId());

                    if (rule.isComplex()) return complexRuleService.getCondition(rule.getId());

                    return Mono.empty();
                },
                (rule, condition) -> {
                    if (condition == null) return Mono.just(Boolean.FALSE);

                    return conditionEvaluator.evaluate(condition, data);
                });
    }
}
