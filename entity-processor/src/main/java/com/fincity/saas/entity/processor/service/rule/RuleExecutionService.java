package com.fincity.saas.entity.processor.service.rule;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.ConditionEvaluator;
import com.fincity.saas.entity.processor.dto.rule.base.RuleConfig;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.google.gson.JsonElement;

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

    public <T extends RuleConfig<T>> Mono<ULong> executeRules(T ruleConfig, JsonElement data) {
        if (ruleConfig == null
                || ruleConfig.getRules() == null
                || ruleConfig.getRules().isEmpty()) {
            return Mono.empty();
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
                        return Mono.empty();
                    }

                    ULong matchedRuleId = matchedRules.getFirst();
                    UserDistribution distribution =
                            ruleConfig.getUserDistributions().get(matchedRuleId);

                    if (distribution == null
                            || distribution.getProfileIds() == null
                            || distribution.getProfileIds().isEmpty()) {
                        return Mono.empty();
                    }

                    return getUsersFromProfiles(distribution.getProfileIds())
                            .flatMap(userIds -> distributeUsers(ruleConfig, distribution, userIds));
                });
    }

    private <T extends RuleConfig<T>> Mono<ULong> distributeUsers(
            T ruleConfig, UserDistribution distribution, List<ULong> userIds) {

        if (userIds == null || userIds.isEmpty()) return Mono.empty();

        DistributionType type = ruleConfig.getUserDistributionType();

        if (type == null) type = DistributionType.RANDOM;

        return switch (type) {
            case ROUND_ROBIN -> this.handleRoundRobin(ruleConfig, userIds);
            case RANDOM -> this.getRandom(userIds);
            case PERCENTAGE -> this.handlePercentage(distribution, userIds);
            case WEIGHTED -> this.handleWeighted(distribution, userIds);
            case LOAD_BALANCED -> this.handleLoadBalanced(distribution, userIds);
            case PRIORITY_QUEUE -> this.handlePriorityQueue(distribution, userIds);
            case HYBRID -> this.handleHybrid(distribution, userIds);
        };
    }

    private <T extends RuleConfig<T>> Mono<ULong> handleRoundRobin(T ruleConfig, List<ULong> userIds) {
        if (userIds == null || userIds.isEmpty()) return Mono.empty();

        List<ULong> sortedUserIds = userIds.stream().sorted().toList();

        ULong lastUsedId = ruleConfig.getLastUsedUserId();
        int currentIndex = 0;

        if (lastUsedId != null) {
            for (int i = 0; i < sortedUserIds.size(); i++) {
                if (sortedUserIds.get(i).equals(lastUsedId)) {
                    currentIndex = (i + 1) % sortedUserIds.size();
                    break;
                }
            }
        }

        ULong nextUserId = sortedUserIds.get(currentIndex);
        ruleConfig.setLastUsedUserId(nextUserId);

        return Mono.just(nextUserId);
    }

    private Mono<ULong> getRandom(List<ULong> userIds) {
        return Mono.just(userIds.get(random.nextInt(userIds.size())));
    }

    // TODO will need to update all these according to requirements right now will focus on ROUND ROBIN

    private Mono<ULong> handlePercentage(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getPercentage() == null || distribution.getPercentage() <= 0) return this.getRandom(userIds);

        int count = (int) Math.ceil((distribution.getPercentage() / 100.0) * userIds.size());
        return Mono.just(userIds.get(Math.min(count - 1, userIds.size() - 1)));
    }

    private Mono<ULong> handleWeighted(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getWeight() == null || distribution.getWeight() <= 0) return this.getRandom(userIds);

        int count = Math.min(distribution.getWeight(), userIds.size());
        return Mono.just(userIds.get(count - 1));
    }

    private Mono<ULong> handleLoadBalanced(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getMaxLoad() == null || distribution.getMaxLoad() <= 0) return this.getRandom(userIds);

        Integer currentCount = distribution.getCurrentCount();
        if (currentCount == null) currentCount = 0;

        if (currentCount >= distribution.getMaxLoad()) return this.getRandom(userIds);

        distribution.setCurrentCount(currentCount + 1);
        return Mono.just(userIds.get(currentCount % userIds.size()));
    }

    private Mono<ULong> handlePriorityQueue(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getPriority() == null || distribution.getPriority() < 0) return this.getRandom(userIds);

        int count = Math.min(distribution.getPriority(), userIds.size() - 1);
        return Mono.just(userIds.get(count));
    }

    private Mono<ULong> handleHybrid(UserDistribution distribution, List<ULong> userIds) {
        if (distribution.getHybridWeights() == null
                || distribution.getHybridWeights().isEmpty()) return this.getRandom(userIds);

        Map<DistributionType, Integer> weights = distribution.getHybridWeights();
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();

        if (totalWeight <= 0) return this.getRandom(userIds);

        int selectedIndex = random.nextInt(totalWeight);
        return Mono.just(userIds.get(selectedIndex % userIds.size()));
    }

    private Mono<Boolean> evaluateRule(ULong ruleId, JsonElement data) {
        return FlatMapUtil.flatMapMonoWithNull(
                () -> ruleService.read(ruleId),
                rule -> {
                    if (rule == null) return Mono.empty();

                    if (rule.isSimple()) return simpleRuleService.getCondition(ruleId);

                    if (rule.isComplex()) return complexRuleService.getCondition(ruleId);

                    return Mono.empty();
                },
                (rule, condition) -> {
                    if (condition == null) return Mono.just(Boolean.FALSE);

                    return conditionEvaluator.evaluate(condition, data);
                });
    }
}
