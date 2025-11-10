package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.service.ConditionEvaluator;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    private final TicketCUserDistributionService userDistributionService;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, ConditionEvaluator> conditionEvaluatorCache = new ConcurrentHashMap<>();

    public RuleExecutionService(TicketCUserDistributionService userDistributionService) {
        this.userDistributionService = userDistributionService;
    }

    public <T extends BaseRuleDto<T>> Mono<T> executeRules(
            ProcessorAccess access, Map<Integer, T> rules, String prefix, JsonElement data) {
        return executeRules(access, rules, prefix, null, data)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleExecutionService.executeRules"));
    }

    public <T extends BaseRuleDto<T>> Mono<T> executeRules(
            ProcessorAccess access, Map<Integer, T> rules, String prefix, ULong userId, JsonElement data) {

        if (rules == null || rules.isEmpty()) return Mono.empty();

        final ULong finalUserId = userId != null && userId.equals(ANO_USER_ID) ? null : userId;

        return this.findMatchedRules(rules, prefix, data)
                .flatMap(matchedRules -> this.handleMatchedRule(access, matchedRules, finalUserId))
                .switchIfEmpty(this.handleDefaultRule(access, rules, finalUserId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleExecutionService.executeRules"));
    }

    private <T extends BaseRuleDto<T>> Mono<T> distributeUsers(T rule, Set<ULong> userIds) {

        if (userIds == null || userIds.isEmpty()) return Mono.empty();

		if (userIds.size() == 1) return Mono.just(this.addAssignedUser(rule, userIds.iterator().next()));

        return switch (rule.getUserDistributionType()) {
            case ROUND_ROBIN -> this.handleRoundRobin(rule, userIds);
            case RANDOM -> this.getRandom(rule, userIds);
            default -> Mono.empty();
        };
    }

    private <T extends BaseRuleDto<T>> Mono<T> handleRoundRobin(T rule, Set<ULong> userIds) {

        TreeSet<ULong> sortedUserIds = new TreeSet<>(userIds);

        ULong lastUsedId = rule.getLastAssignedUserId();

        ULong nextUserId = lastUsedId == null ? sortedUserIds.first() : sortedUserIds.higher(lastUsedId);

        if (nextUserId == null) nextUserId = sortedUserIds.first();

        return Mono.just(addAssignedUser(rule, nextUserId));
    }

    private <T extends BaseRuleDto<T>> Mono<T> getRandom(T rule, Set<ULong> userIds) {
	    ULong last = rule.getLastAssignedUserId();
	    if (last != null)
		    userIds.remove(last);

        return Mono.fromSupplier(() -> userIds.stream()
                        .skip(random.nextInt(userIds.size()))
                        .findFirst()
                        .orElse(null))
                .flatMap(userId -> userId == null ? Mono.empty() : Mono.just(addAssignedUser(rule, userId)));
    }

    // TODO Add other distribution apart from ROUND ROBIN & RANDOM

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
                .concatMap(rule -> {
                    if (rule.getCondition() == null) return Mono.empty();

                    ConditionEvaluator con = conditionEvaluatorCache.computeIfAbsent(prefix, ConditionEvaluator::new);

                    return con.evaluate(rule.getCondition(), data)
                            .filter(Boolean::booleanValue)
                            .mapNotNull(match -> rule);
                })
                .next();
    }

    private <T extends BaseRuleDto<T>> Mono<T> handleDefaultRule(
            ProcessorAccess access, Map<Integer, T> rules, ULong finalUserId) {
        T defaultRule = rules.get(0);
        if (defaultRule == null) return Mono.empty();

        return this.userDistributionService
                .getUsersByRuleId(access, defaultRule.getId())
                .flatMap(userIds -> {
                    // If userId is provided and exists in default rule's userIds, use it
                    if (finalUserId != null && userIds.contains(finalUserId))
                        return Mono.just(this.addAssignedUser(defaultRule, finalUserId));

                    // Otherwise distribute users according to the rule
                    return this.distributeUsers(defaultRule, userIds);
                });
    }

    private <T extends BaseRuleDto<T>> Mono<T> handleMatchedRule(
            ProcessorAccess access, T matchedRule, ULong finalUserId) {
        return this.userDistributionService
                .getUsersByRuleId(access, matchedRule.getId())
                .flatMap(userIds -> {
                    // Case 1: finalUserId is provided and exists in the rule's userIds
                    if (finalUserId != null && userIds.contains(finalUserId))
                        return Mono.just(this.addAssignedUser(matchedRule, finalUserId));

                    // Case 2: finalUserId is null or not found in any rule
                    return this.distributeUsers(matchedRule, userIds);
                });
    }
}
