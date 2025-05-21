package com.fincity.saas.entity.processor.service.rule;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.service.ConditionEvaluator;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.google.gson.JsonElement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RuleExecutionService {

    private final SimpleRuleService simpleRuleService;
    private final ComplexRuleService complexRuleService;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, ConditionEvaluator> conditionEvaluatorCache = new ConcurrentHashMap<>();

    public RuleExecutionService(SimpleRuleService simpleRuleService, ComplexRuleService complexRuleService) {
        this.simpleRuleService = simpleRuleService;
        this.complexRuleService = complexRuleService;
    }

    private Mono<List<ULong>> getUsersForDistribution(UserDistribution userDistribution) {

        if (userDistribution == null) return Mono.empty();

        if ((userDistribution.getProfileIds() == null
                        || userDistribution.getProfileIds().isEmpty())
                && (userDistribution.getUserIds() == null
                        || userDistribution.getUserIds().isEmpty())) return Mono.empty();

        // TODO: Implement actual logic to fetch users from profiles
        // For now, return an empty list
        return Mono.just(userDistribution.getUserIds());
    }

    public <T extends Rule<T>> Mono<T> executeRules(Map<Integer, T> rules, String prefix, JsonElement data) {

        if (rules == null || rules.isEmpty()) return Mono.empty();

        return Flux.fromIterable(rules.entrySet())
                .sort(Map.Entry.comparingByKey())
                .flatMap(entry -> this.evaluateRule(entry.getValue(), prefix, data)
                        .filter(matches -> matches)
                        .map(matches -> entry.getValue()))
                .take(1) // Break at first match
                .collectList()
                .flatMap(matchedRules -> {
                    if (matchedRules.isEmpty()) return Mono.empty();

                    T matchedRule = matchedRules.getFirst();

                    return getUsersForDistribution(matchedRule.getUserDistribution())
                            .flatMap(userIds -> this.distributeUsers(matchedRule, userIds));
                });
    }

    private <T extends Rule<T>> Mono<T> distributeUsers(T rule, List<ULong> userIds) {

        if (userIds == null || userIds.isEmpty()) return Mono.empty();

        DistributionType type = rule.getUserDistributionType();

        if (type == null) type = DistributionType.RANDOM;

        return switch (type) {
            case ROUND_ROBIN -> this.handleRoundRobin(rule, userIds);
            case RANDOM -> this.getRandom(rule, userIds);
            case PERCENTAGE -> this.handlePercentage(rule, userIds);
            case WEIGHTED -> this.handleWeighted(rule, userIds);
            case LOAD_BALANCED -> this.handleLoadBalanced(rule, userIds);
            case PRIORITY_QUEUE -> this.handlePriorityQueue(rule, userIds);
            case HYBRID -> this.handleHybrid(rule, userIds);
        };
    }

    private <T extends Rule<T>> Mono<T> handleRoundRobin(T rule, List<ULong> userIds) {
        if (userIds == null || userIds.isEmpty()) return Mono.empty();

        TreeSet<ULong> sortedUserIds = new TreeSet<>(userIds);

        ULong lastUsedId = rule.getLastAssignedUserId();

        ULong nextUserId = lastUsedId == null ? sortedUserIds.first() : sortedUserIds.higher(lastUsedId);

        if (nextUserId == null) nextUserId = sortedUserIds.first();

        return Mono.just(addAssignedUser(rule, nextUserId));
    }

    private <T extends Rule<T>> Mono<T> getRandom(T rule, List<ULong> userIds) {
        return Mono.just(userIds.get(random.nextInt(userIds.size()))).map(userId -> this.addAssignedUser(rule, userId));
    }

    // TODO will need to update all these according to requirements right now will focus on ROUND ROBIN

    private <T extends Rule<T>> Mono<T> handlePercentage(T rule, List<ULong> userIds) {

        UserDistribution distribution = rule.getUserDistribution();

        if (distribution.getPercentage() == null || distribution.getPercentage() <= 0)
            return this.getRandom(rule, userIds);

        int count = (int) Math.ceil((distribution.getPercentage() / 100.0) * userIds.size());
        return Mono.just(userIds.get(Math.min(count - 1, userIds.size() - 1)))
                .map(userId -> this.addAssignedUser(rule, userId));
    }

    private <T extends Rule<T>> Mono<T> handleWeighted(T rule, List<ULong> userIds) {

        UserDistribution distribution = rule.getUserDistribution();

        if (distribution.getWeight() == null || distribution.getWeight() <= 0) return this.getRandom(rule, userIds);

        int count = Math.min(distribution.getWeight(), userIds.size());
        return Mono.just(userIds.get(count - 1)).map(userId -> this.addAssignedUser(rule, userId));
    }

    private <T extends Rule<T>> Mono<T> handleLoadBalanced(T rule, List<ULong> userIds) {

        UserDistribution distribution = rule.getUserDistribution();

        if (distribution.getMaxLoad() == null || distribution.getMaxLoad() <= 0) return this.getRandom(rule, userIds);

        Integer currentCount = distribution.getCurrentCount();
        if (currentCount == null) currentCount = 0;

        if (currentCount >= distribution.getMaxLoad()) return this.getRandom(rule, userIds);

        distribution.setCurrentCount(currentCount + 1);
        return Mono.just(userIds.get(currentCount % userIds.size())).map(userId -> this.addAssignedUser(rule, userId));
    }

    private <T extends Rule<T>> Mono<T> handlePriorityQueue(T rule, List<ULong> userIds) {

        UserDistribution distribution = rule.getUserDistribution();

        if (distribution.getPriority() == null || distribution.getPriority() < 0) return this.getRandom(rule, userIds);

        int count = Math.min(distribution.getPriority(), userIds.size() - 1);
        return Mono.just(userIds.get(count)).map(userId -> this.addAssignedUser(rule, userId));
    }

    private <T extends Rule<T>> Mono<T> handleHybrid(T rule, List<ULong> userIds) {

        UserDistribution distribution = rule.getUserDistribution();

        if (distribution.getHybridWeights() == null
                || distribution.getHybridWeights().isEmpty()) return this.getRandom(rule, userIds);

        Map<DistributionType, Integer> weights = distribution.getHybridWeights();
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();

        if (totalWeight <= 0) return this.getRandom(rule, userIds);

        int selectedIndex = random.nextInt(totalWeight);
        return Mono.just(userIds.get(selectedIndex % userIds.size())).map(userId -> this.addAssignedUser(rule, userId));
    }

    private <T extends Rule<T>> Mono<Boolean> evaluateRule(T rule, String tokenPrefix, JsonElement data) {
        return FlatMapUtil.flatMapMonoWithNull(
                () -> {
                    if (rule == null) return Mono.empty();

                    if (rule.isSimple()) return simpleRuleService.getCondition(rule.getId());

                    if (rule.isComplex()) return complexRuleService.getCondition(rule.getId());

                    return Mono.empty();
                },
                condition -> {
                    if (condition == null) return Mono.just(Boolean.FALSE);

                    ConditionEvaluator con =
                            conditionEvaluatorCache.computeIfAbsent(tokenPrefix, ConditionEvaluator::new);

                    return con.evaluate(condition, data);
                });
    }

    @SuppressWarnings("unchecked")
    private <T extends Rule<T>> T addAssignedUser(T rule, ULong assignedUserId) {
        return (T) rule.setLastAssignedUserId(assignedUserId);
    }
}
