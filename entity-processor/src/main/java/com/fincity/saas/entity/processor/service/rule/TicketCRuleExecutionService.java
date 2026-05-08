package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.service.ConditionEvaluator;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRule;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
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
@IgnoreGeneration
public class TicketCRuleExecutionService {

    private static final ULong ANO_USER_ID = ULong.MIN;

    private final TicketCUserDistributionService userDistributionService;
    private final Random random = new Random();
    private final ConcurrentHashMap<String, ConditionEvaluator> conditionEvaluatorCache = new ConcurrentHashMap<>();

    public TicketCRuleExecutionService(TicketCUserDistributionService userDistributionService) {
        this.userDistributionService = userDistributionService;
    }

    public Mono<ProductTicketCRule> executeRules(
            ProcessorAccess access, Map<Integer, ProductTicketCRule> rules, String prefix, JsonElement data) {
        return executeRules(access, rules, prefix, null, data)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleExecutionService.executeRules"));
    }

    public Mono<ProductTicketCRule> executeRules(
            ProcessorAccess access,
            Map<Integer, ProductTicketCRule> rules,
            String prefix,
            ULong userId,
            JsonElement data) {
        return this.executeRules(access, rules, prefix, userId, data, null);
    }

    public Mono<ProductTicketCRule> executeRules(
            ProcessorAccess access,
            Map<Integer, ProductTicketCRule> rules,
            String prefix,
            ULong userId,
            JsonElement data,
            Map<String, Object> trace) {

        if (rules == null || rules.isEmpty()) {
            if (trace != null) trace.put("executeRules_empty", true);
            return Mono.empty();
        }

        final ULong finalUserId = userId != null && userId.equals(ANO_USER_ID) ? null : userId;

        if (trace != null) {
            trace.put("executeRules_ruleCount", rules.size());
            trace.put("executeRules_ruleOrders", rules.keySet().toString());
            trace.put("executeRules_inputUserId", userId != null ? userId.toString() : null);
            trace.put("executeRules_finalUserId", finalUserId != null ? finalUserId.toString() : null);
        }

        return this.findMatchedRules(rules, prefix, data, trace)
                .flatMap(matchedRule -> {
                    if (trace != null) {
                        trace.put("matchedRuleType", "CONDITIONAL");
                        trace.put("matchedRuleId", matchedRule.getId() != null ? matchedRule.getId().toString() : null);
                        trace.put("matchedRuleOrder", matchedRule.getOrder());
                    }
                    return this.handleMatchedRule(access, matchedRule, finalUserId, trace);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    if (trace != null) trace.put("conditionalRuleMatched", false);
                    return this.handleDefaultRule(access, rules, finalUserId, trace);
                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleExecutionService.executeRules"));
    }

    private Mono<ProductTicketCRule> distributeUsers(
            ProductTicketCRule rule, Set<ULong> userIds, Map<String, Object> trace) {

        if (userIds == null || userIds.isEmpty()) {
            if (trace != null) trace.put("distributeResult", "NO_USERS_AVAILABLE");
            return Mono.empty();
        }

        if (trace != null) {
            trace.put("distributeUserCount", userIds.size());
            trace.put("distributeUserIds", userIds.toString());
            trace.put("distributeType", rule.getUserDistributionType() != null
                    ? rule.getUserDistributionType().getLiteral() : null);
            trace.put("distributeLastAssignedUserId",
                    rule.getLastAssignedUserId() != null ? rule.getLastAssignedUserId().toString() : null);
        }

        if (userIds.size() == 1) {
            ULong onlyUser = userIds.iterator().next();
            if (trace != null) trace.put("distributeResult", "SINGLE_USER_" + onlyUser);
            return Mono.just(this.addAssignedUser(rule, onlyUser));
        }

        return switch (rule.getUserDistributionType()) {
            case ROUND_ROBIN -> this.handleRoundRobin(rule, userIds);
            case RANDOM -> this.getRandom(rule, userIds);
            default -> {
                if (trace != null)
                    trace.put("distributeResult", "UNSUPPORTED_TYPE_" + rule.getUserDistributionType());
                yield Mono.empty();
            }
        };
    }

    private Mono<ProductTicketCRule> handleRoundRobin(ProductTicketCRule rule, Set<ULong> userIds) {

        TreeSet<ULong> sortedUserIds = new TreeSet<>(userIds);

        ULong lastUsedId = rule.getLastAssignedUserId();

        ULong nextUserId = lastUsedId == null ? sortedUserIds.first() : sortedUserIds.higher(lastUsedId);

        if (nextUserId == null) nextUserId = sortedUserIds.first();

        return Mono.just(addAssignedUser(rule, nextUserId));
    }

    private Mono<ProductTicketCRule> getRandom(ProductTicketCRule rule, Set<ULong> userIds) {
        ULong last = rule.getLastAssignedUserId();
        if (last != null) userIds.remove(last);

        return Mono.fromSupplier(() -> userIds.stream()
                        .skip(random.nextInt(userIds.size()))
                        .findFirst()
                        .orElse(null))
                .flatMap(userId -> userId == null ? Mono.empty() : Mono.just(addAssignedUser(rule, userId)));
    }

    // TODO Add other distribution apart from ROUND ROBIN & RANDOM

    private ProductTicketCRule addAssignedUser(ProductTicketCRule rule, ULong assignedUserId) {
        return (ProductTicketCRule) rule.setLastAssignedUserId(assignedUserId);
    }

    private Mono<ProductTicketCRule> findMatchedRules(
            Map<Integer, ProductTicketCRule> rules, String prefix, JsonElement data, Map<String, Object> trace) {

        if (rules == null || rules.isEmpty()) return Mono.empty();

        ConditionEvaluator evaluator = conditionEvaluatorCache.computeIfAbsent(prefix, ConditionEvaluator::new);

        List<Map<String, Object>> conditionResults = trace != null ? new ArrayList<>() : null;

        return Flux.fromStream(rules.entrySet().stream()
                        .filter(e -> e.getKey() != null && e.getKey() > 0)
                        .sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue))
                .concatMap(rule -> {
                    if (rule.getCondition() == null) {
                        if (conditionResults != null) {
                            Map<String, Object> entry = new HashMap<>();
                            entry.put("ruleId", rule.getId() != null ? rule.getId().toString() : null);
                            entry.put("order", rule.getOrder());
                            entry.put("result", "NO_CONDITION");
                            conditionResults.add(entry);
                        }
                        return Mono.empty();
                    }
                    return evaluator
                            .evaluate(rule.getCondition(), data)
                            .doOnNext(result -> {
                                if (conditionResults != null) {
                                    Map<String, Object> entry = new HashMap<>();
                                    entry.put("ruleId", rule.getId() != null ? rule.getId().toString() : null);
                                    entry.put("order", rule.getOrder());
                                    entry.put("result", result.toString());
                                    conditionResults.add(entry);
                                }
                            })
                            .onErrorResume(e -> {
                                if (conditionResults != null) {
                                    Map<String, Object> entry = new HashMap<>();
                                    entry.put("ruleId", rule.getId() != null ? rule.getId().toString() : null);
                                    entry.put("order", rule.getOrder());
                                    entry.put("result", "ERROR");
                                    entry.put("error", e.getMessage());
                                    conditionResults.add(entry);
                                }
                                return Mono.just(Boolean.FALSE);
                            })
                            .filter(Boolean::booleanValue)
                            .map(b -> rule);
                })
                .next()
                .doFinally(signal -> {
                    if (trace != null && conditionResults != null)
                        trace.put("conditionEvaluations", conditionResults);
                });
    }

    private Mono<ProductTicketCRule> handleDefaultRule(
            ProcessorAccess access, Map<Integer, ProductTicketCRule> rules, ULong finalUserId,
            Map<String, Object> trace) {

        ProductTicketCRule defaultRule = rules.get(0);
        if (defaultRule == null) {
            if (trace != null) {
                trace.put("defaultRuleFound", false);
                trace.put("matchedRuleType", "NONE");
            }
            return Mono.empty();
        }

        if (trace != null) {
            trace.put("defaultRuleFound", true);
            trace.put("defaultRuleId", defaultRule.getId() != null ? defaultRule.getId().toString() : null);
            trace.put("matchedRuleType", "DEFAULT");
            trace.put("matchedRuleId", defaultRule.getId() != null ? defaultRule.getId().toString() : null);
            trace.put("matchedRuleOrder", 0);
        }

        return this.userDistributionService
                .getUsersByRuleId(access, defaultRule.getId())
                .switchIfEmpty(Mono.defer(() -> {
                    if (trace != null) trace.put("userResolution", "GET_USERS_RETURNED_EMPTY");
                    return Mono.empty();
                }))
                .flatMap(userIds -> {
                    if (trace != null) {
                        trace.put("userPoolSize", userIds.size());
                        trace.put("userPoolIds", userIds.toString());
                    }

                    if (finalUserId != null && userIds.contains(finalUserId)) {
                        if (trace != null) trace.put("assignmentReason", "LOGGED_IN_USER_IN_POOL");
                        return Mono.just(this.addAssignedUser(defaultRule, finalUserId));
                    }

                    if (trace != null) trace.put("assignmentReason", "DISTRIBUTION");
                    return this.distributeUsers(defaultRule, userIds, trace);
                });
    }

    private Mono<ProductTicketCRule> handleMatchedRule(
            ProcessorAccess access, ProductTicketCRule matchedRule, ULong finalUserId,
            Map<String, Object> trace) {

        return this.userDistributionService
                .getUsersByRuleId(access, matchedRule.getId())
                .switchIfEmpty(Mono.defer(() -> {
                    if (trace != null) trace.put("userResolution", "GET_USERS_RETURNED_EMPTY");
                    return Mono.empty();
                }))
                .flatMap(userIds -> {
                    if (trace != null) {
                        trace.put("userPoolSize", userIds.size());
                        trace.put("userPoolIds", userIds.toString());
                    }

                    if (finalUserId != null && userIds.contains(finalUserId)) {
                        if (trace != null) trace.put("assignmentReason", "LOGGED_IN_USER_IN_POOL");
                        return Mono.just(this.addAssignedUser(matchedRule, finalUserId));
                    }

                    if (trace != null) trace.put("assignmentReason", "DISTRIBUTION");
                    return this.distributeUsers(matchedRule, userIds, trace);
                });
    }
}
