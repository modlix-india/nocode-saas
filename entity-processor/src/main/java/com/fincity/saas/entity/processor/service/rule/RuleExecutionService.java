package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.ConditionEvaluator;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class RuleExecutionService {

    private static final ULong ANO_USER_ID = ULong.MIN;

    private final SimpleRuleService simpleRuleService;
    private final ComplexRuleService complexRuleService;
    private final IFeignSecurityService securityService;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, ConditionEvaluator> conditionEvaluatorCache = new ConcurrentHashMap<>();

    public RuleExecutionService(
            SimpleRuleService simpleRuleService,
            ComplexRuleService complexRuleService,
            IFeignSecurityService securityService) {
        this.simpleRuleService = simpleRuleService;
        this.complexRuleService = complexRuleService;
        this.securityService = securityService;
    }

    private Mono<List<ULong>> getUsersForDistribution(UserDistribution userDistribution) {

        if (userDistribution == null) return Mono.empty();

        if ((userDistribution.getProfileIds() == null
                        || userDistribution.getProfileIds().isEmpty())
                && (userDistribution.getUserIds() == null
                        || userDistribution.getUserIds().isEmpty())) return Mono.empty();

        if (userDistribution.getProfileIds() != null
                && !userDistribution.getProfileIds().isEmpty()) {
            String appCode = userDistribution.getAppCode();

            return this.securityService
                    .getProfileUsers(appCode, userDistribution.getProfileIdsInt())
                    .flatMap(userIds -> {
                        List<ULong> combinedUserIds = new ArrayList<>();

                        if (userIds != null && !userIds.isEmpty())
                            combinedUserIds.addAll(
                                    userIds.stream().map(ULongUtil::valueOf).toList());

                        if (userDistribution.getUserIds() != null
                                && !userDistribution.getUserIds().isEmpty())
                            combinedUserIds.addAll(userDistribution.getUserIds());

                        return Mono.just(combinedUserIds);
                    });
        }

        return Mono.just(userDistribution.getUserIds());
    }

    public <T extends Rule<T>> Mono<T> executeRules(Map<Integer, T> rules, String prefix, JsonElement data) {
        return executeRules(rules, prefix, null, data)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleExecutionService.executeRules"));
    }

    public <T extends Rule<T>> Mono<T> executeRules(
            Map<Integer, T> rules, String prefix, ULong userId, JsonElement data) {

        if (rules == null || rules.isEmpty()) return Mono.empty();

        final ULong finalUserId = userId != null && userId.equals(ANO_USER_ID) ? null : userId;

        return findMatchedRules(rules, prefix, data)
                .flatMap(matchedRules -> {
                    if (matchedRules.isEmpty()) return handleDefaultRule(rules, finalUserId);

                    T matchedRule = matchedRules.getFirst();
                    return handleMatchedRule(matchedRule, finalUserId);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleExecutionService.executeRules"));
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

                    if (rule.isSimple())
                        return simpleRuleService.getCondition(rule.getId(), rule.getEntitySeries(), Boolean.FALSE);

                    if (rule.isComplex()) return complexRuleService.getCondition(rule.getId(), rule.getEntitySeries());

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

    private <T extends Rule<T>> Mono<List<T>> findMatchedRules(Map<Integer, T> rules, String prefix, JsonElement data) {
        return Flux.fromIterable(rules.keySet())
                .filter(order -> order != null && order > 0)
                .sort((o1, o2) -> Integer.compare(o2, o1))
                .map(rules::get)
                .flatMap(rule -> this.evaluateRule(rule, prefix, data)
                        .filter(matches -> matches)
                        .map(matches -> rule))
                .take(1)
                .collectList();
    }

    private <T extends Rule<T>> Mono<T> handleDefaultRule(Map<Integer, T> rules, ULong finalUserId) {
        T defaultRule = rules.get(0);
        if (defaultRule == null) return Mono.empty();

        return this.getUsersForDistribution(defaultRule.getUserDistribution()).flatMap(userIds -> {
            // If userId is provided and exists in default rule's userIds, use it
            if (finalUserId != null && userIds.contains(finalUserId))
                return Mono.just(this.addAssignedUser(defaultRule, finalUserId));

            // Otherwise distribute users according to the rule
            return this.distributeUsers(defaultRule, userIds);
        });
    }

    private <T extends Rule<T>> Mono<T> handleMatchedRule(T matchedRule, ULong finalUserId) {
        return this.getUsersForDistribution(matchedRule.getUserDistribution()).flatMap(userIds -> {
            // Case 1: finalUserId is provided and exists in the rule's userIds
            if (finalUserId != null && userIds.contains(finalUserId))
                return Mono.just(this.addAssignedUser(matchedRule, finalUserId));

            // Case 2: finalUserId is null or not found in any rule
            return this.distributeUsers(matchedRule, userIds);
        });
    }
}
