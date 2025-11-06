package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.ConditionEvaluator;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
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

    private final IFeignSecurityService securityService;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, ConditionEvaluator> conditionEvaluatorCache = new ConcurrentHashMap<>();

    public RuleExecutionService(IFeignSecurityService securityService) {
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

    public <T extends BaseRuleDto<T>> Mono<T> executeRules(Map<Integer, T> rules, String prefix, JsonElement data) {
        return executeRules(rules, prefix, null, data)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleExecutionService.executeRules"));
    }

    public <T extends BaseRuleDto<T>> Mono<T> executeRules(
            Map<Integer, T> rules, String prefix, ULong userId, JsonElement data) {

        if (rules == null || rules.isEmpty()) return Mono.empty();

        final ULong finalUserId = userId != null && userId.equals(ANO_USER_ID) ? null : userId;

        return this.findMatchedRules(rules, prefix, data)
                .flatMap(matchedRules -> this.handleMatchedRule(matchedRules, finalUserId))
                .switchIfEmpty(this.handleDefaultRule(rules, finalUserId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleExecutionService.executeRules"));
    }

    private <T extends BaseRuleDto<T>> Mono<T> distributeUsers(T rule, List<ULong> userIds) {

        if (userIds == null || userIds.isEmpty()) return Mono.empty();

        return switch (rule.getUserDistributionType()) {
            case ROUND_ROBIN -> this.handleRoundRobin(rule, userIds);
            case RANDOM -> this.getRandom(rule, userIds);
            default -> Mono.empty();
        };
    }

    private <T extends BaseRuleDto<T>> Mono<T> handleRoundRobin(T rule, List<ULong> userIds) {

        TreeSet<ULong> sortedUserIds = new TreeSet<>(userIds);

        ULong lastUsedId = rule.getLastAssignedUserId();

        ULong nextUserId = lastUsedId == null ? sortedUserIds.first() : sortedUserIds.higher(lastUsedId);

        if (nextUserId == null) nextUserId = sortedUserIds.first();

        return Mono.just(addAssignedUser(rule, nextUserId));
    }

    private <T extends BaseRuleDto<T>> Mono<T> getRandom(T rule, List<ULong> userIds) {
        return Mono.just(userIds.get(random.nextInt(userIds.size()))).map(userId -> this.addAssignedUser(rule, userId));
    }

    // TODO will need to update all these according to requirements right now will focus on ROUND ROBIN

    @SuppressWarnings("unchecked")
    private <T extends BaseRuleDto<T>> T addAssignedUser(T rule, ULong assignedUserId) {
        return (T) rule.setLastAssignedUserId(assignedUserId);
    }

    private <T extends BaseRuleDto<T>> Mono<T> findMatchedRules(
            Map<Integer, T> rules, String prefix, JsonElement data) {
        if (rules == null || rules.isEmpty()) return Mono.empty();

        List<T> sortedRules = rules.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey() > 0)
                .sorted((e1, e2) -> Integer.compare(e2.getKey(), e1.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        return Flux.fromIterable(sortedRules)
                .concatMap(rule -> this.getConditionForRule(rule).flatMap(condition -> {
                    if (condition == null) return Mono.empty();

                    ConditionEvaluator con = conditionEvaluatorCache.computeIfAbsent(prefix, ConditionEvaluator::new);

                    return con.evaluate(condition, data)
                            .filter(Boolean::booleanValue)
                            .mapNotNull(match -> rule);
                }))
                .next();
    }

    private <T extends BaseRuleDto<T>> Mono<AbstractCondition> getConditionForRule(T rule) {
        if (rule == null) return Mono.empty();
        return Mono.just(rule.getCondition());
    }

    private <T extends BaseRuleDto<T>> Mono<T> handleDefaultRule(Map<Integer, T> rules, ULong finalUserId) {
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

    private <T extends BaseRuleDto<T>> Mono<T> handleMatchedRule(T matchedRule, ULong finalUserId) {
        return this.getUsersForDistribution(matchedRule.getUserDistribution()).flatMap(userIds -> {
            // Case 1: finalUserId is provided and exists in the rule's userIds
            if (finalUserId != null && userIds.contains(finalUserId))
                return Mono.just(this.addAssignedUser(matchedRule, finalUserId));

            // Case 2: finalUserId is null or not found in any rule
            return this.distributeUsers(matchedRule, userIds);
        });
    }
}
