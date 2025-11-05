package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.google.gson.JsonElement;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class BaseRuleService<
                R extends UpdatableRecord<R>, D extends BaseRuleDto<D>, O extends BaseRuleDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    private static final String DEFAULT_KEY = "default";

    private static final int DEFAULT_ORDER = BigInteger.ZERO.intValue();

    protected RuleExecutionService ruleExecutionService;
    protected StageService stageService;

    protected abstract Mono<Identity> getEntityId(ProcessorAccess access, Identity entityId);

    protected abstract Mono<Set<ULong>> getStageIds(ProcessorAccess access, Identity entityId, List<ULong> stageIds);

    protected abstract Mono<ULong> getUserAssignment(
            ProcessorAccess access, ULong entityId, ULong stageId, String tokenPrefix, ULong userId, JsonElement data);

    @Autowired
    private void setRuleExecutionService(RuleExecutionService ruleExecutionService) {
        this.ruleExecutionService = ruleExecutionService;
    }

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<D> checkEntity(D entity, ProcessorAccess access) {

        if (entity.getOrder() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.RULE_ORDER_MISSING);

        return this.getRule(access, entity.getEntityId(), entity.getOrder())
                .flatMap(existing -> {
                    if (existing == null) return Mono.just(entity);

                    return this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            ProcessorMessageResourceService.DUPLICATE_RULE_ORDER,
                            existing.getId(),
                            entity.getOrder());
                })
                .switchIfEmpty(Mono.just(entity));
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return Mono.zip(
                super.evictCache(entity),
                this.evictRulesCache(
                        entity.getAppCode(), entity.getClientCode(), entity.getEntityId(), entity.getStageId()),
                (baseEvicted, rulesEvicted) -> baseEvicted && rulesEvicted);
    }

    private Mono<Boolean> evictRulesCache(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return Mono.zip(
                this.cacheService.evict(this.getCacheName(), this.getCacheKey(appCode, clientCode, entityId, stageId)),
                this.evictDefaultCache(appCode, clientCode, entityId),
                (ruleEvicted, defaultEvicted) -> ruleEvicted && defaultEvicted);
    }

    private Mono<Boolean> evictDefaultCache(String appCode, String clientCode, ULong entityId) {
        return this.cacheService.evict(
                this.getCacheName(), this.getCacheKey(appCode, clientCode, entityId, DEFAULT_KEY));
    }

    @Override
    protected Mono<D> updatableEntity(D rule) {
        return super.updatableEntity(rule).flatMap(existing -> {
            existing.setStageId(rule.getStageId());

            if (rule.getOrder() == DEFAULT_ORDER) {
                existing.setOrder(0);
                existing.setIsDefault(Boolean.TRUE);
            }

            if (rule.getOrder() > DEFAULT_ORDER) existing.setOrder(rule.getOrder());

            existing.setBreakAtFirstMatch(rule.isBreakAtFirstMatch());
            existing.setUserDistributionType(rule.getUserDistributionType());
            existing.setUserDistribution(existing.getUserDistribution().update(rule.getUserDistribution()));
            existing.setLastAssignedUserId(rule.getLastAssignedUserId());
            existing.setCondition(rule.getCondition());

            return Mono.just(existing);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<D> create(D entity) {

        if (entity.getCondition() == null || entity.getCondition().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.RULE_CONDITION_MISSING,
                    entity.getOrder());

        if (entity.getUserDistribution() == null) entity.setUserDistribution(new UserDistribution());

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> Mono.just((D) entity.setUserDistribution(entity.getUserDistribution()
                        .setAppCode(access.getAppCode())
                        .setClientCode(access.getClientCode()))),
                super::create);
    }

    public Mono<Map<Integer, D>> getRulesWithOrder(ProcessorAccess access, ULong entityId, ULong stageId) {

        if (entityId == null || stageId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                        () -> Mono.zip(this.getRule(access, entityId, stageId), this.getDefaultRule(access, entityId)),
                        rules -> {
                            Map<Integer, D> rulesMap = rules.getT1().stream()
                                    .collect(Collectors.toMap(BaseRuleDto::getOrder, Function.identity(), (a, b) -> b));

                            rulesMap.put(0, rules.getT2());

                            return Mono.just(rulesMap);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleService.getRulesWithOrder"));
    }

    public Mono<Map<Integer, D>> getRulesWithOrder(Identity entity, List<ULong> stageIds, boolean getDefault) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess, access -> this.getRulesWithOrderInternal(access, entity, stageIds, getDefault)
                        .map(TreeMap::new));
    }

    private Mono<Map<Integer, D>> getRulesWithOrderInternal(
            ProcessorAccess access, Identity entity, List<ULong> stageIds, boolean getDefault) {

        if (entity == null || stageIds == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> this.getStageIds(access, entity, stageIds),
                filterStageIds -> this.getEntityId(access, entity),
                (filterStageIds, entityId) -> this.getRules(
                        access, entityId.getULongId(), filterStageIds.stream().toList(), getDefault),
                (filterStageIds, entityId, rules) -> Mono.just(rules.stream()
                        .collect(Collectors.toMap(BaseRuleDto::getOrder, Function.identity(), (a, b) -> b))));
    }

    public Mono<Map<Integer, D>> createWithOrder(Identity entityId, Map<Integer, D> ruleRequest) {

        if (ruleRequest == null || ruleRequest.isEmpty()) return Mono.just(Map.of());

        if (!ruleRequest.containsKey(0))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DEFAULT_RULE_MISSING);

        for (Map.Entry<Integer, D> rule : ruleRequest.entrySet())
            if (rule.getValue().getCondition() == null
                    || rule.getValue().getCondition().isEmpty())
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.RULE_CONDITION_MISSING,
                        rule.getKey());

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.getRulesWithOrderInternal(access, entityId, null, Boolean.TRUE)
                                .switchIfEmpty(Mono.just(Map.of())),
                        (access, rules) ->
                                this.deleteMissingRules(rules, ruleRequest).then(Mono.just(Boolean.TRUE)),
                        (access, rules, deleted) -> updateOrCreateRule(access, rules, ruleRequest))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleService.createWithOrder"));
    }

    private Mono<Void> deleteMissingRules(Map<Integer, D> existingRules, Map<Integer, D> incomingRules) {
        if (existingRules == null || existingRules.isEmpty()) return Mono.empty();

        List<ULong> idsToDelete = existingRules.entrySet().stream()
                .filter(entry -> !incomingRules.containsKey(entry.getKey()))
                .map(entry -> entry.getValue().getId())
                .toList();

        if (idsToDelete.isEmpty()) return Mono.empty();

        return Flux.fromIterable(idsToDelete).flatMap(this::delete).then();
    }

    private Mono<Map<Integer, D>> updateOrCreateRule(
            ProcessorAccess access, Map<Integer, D> existingRules, Map<Integer, D> incomingRules) {
        return Flux.fromIterable(incomingRules.entrySet())
                .flatMap(entry -> {
                    Integer order = entry.getKey();
                    D incomingRule = entry.getValue();
                    incomingRule.setOrder(order);
                    incomingRule.setUserDistribution(incomingRule
                            .getUserDistribution()
                            .setAppCode(access.getAppCode())
                            .setClientCode(access.getClientCode()));

                    if (order == DEFAULT_ORDER)
                        incomingRule.setIsDefault(Boolean.TRUE).setStageId(null);

                    D existingRule = existingRules.get(order);

                    if (existingRule != null) {
                        incomingRule.setId(existingRule.getId());
                        return super.updateInternal(access, incomingRule)
                                .map(updated -> Map.entry(updated.getOrder(), updated));
                    } else {
                        return this.createInternal(access, incomingRule)
                                .map(created -> Map.entry(created.getOrder(), created));
                    }
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Map<Integer, D>> updateOrder(Identity entityId, Map<Integer, Identity> ruleRequests) {

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> this.getRulesWithOrderInternal(access, entityId, null, Boolean.FALSE),
                        (access, rules) -> {
                            ruleRequests.remove(DEFAULT_ORDER);

                            if (!rules.keySet().equals(ruleRequests.keySet()))
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        ProcessorMessageResourceService.INVALID_RULE_ORDER);

                            return Flux.fromIterable(ruleRequests.entrySet())
                                    .flatMap(entry -> this.updateOrder(access, entry.getValue(), entry.getKey())
                                            .map(updatedRule -> Map.entry(updatedRule.getOrder(), updatedRule)))
                                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);
                        })
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.RULES_MISSING,
                        this.getEntityName(),
                        entityId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleService.updateOrder"));
    }

    private Mono<D> updateOrder(ProcessorAccess access, Identity ruleId, Integer order) {
        return super.readIdentityWithAccess(access, ruleId).flatMap(rule -> {
            rule.setOrder(order);
            return super.updateInternal(access, rule);
        });
    }

    public Mono<List<D>> getRule(ProcessorAccess access, ULong entityId, ULong stageId) {

        if (entityId == null || stageId == null) return Mono.empty();

        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.dao.getRules(access, entityId, stageId),
                        this.getCacheKey(access.getAppCode(), access.getClientCode(), entityId, stageId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleService.getRule"));
    }

    public Mono<D> getDefaultRule(ProcessorAccess access, ULong entityId) {

        if (entityId == null) return Mono.empty();

        return this.cacheService
                .cacheValueOrGet(
                        this.getCacheName(),
                        () -> this.dao.getRule(access, entityId, DEFAULT_ORDER),
                        this.getCacheKey(access.getAppCode(), access.getClientCode(), entityId, DEFAULT_KEY))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RuleService.getDefaultRule"));
    }

    private Mono<List<D>> getRules(ProcessorAccess access, ULong entityId, List<ULong> stageIds, boolean getDefault) {
        return this.dao.getRules(access, entityId, stageIds, getDefault);
    }

    private Mono<D> getRule(ProcessorAccess access, ULong entityId, Integer order) {
        return this.dao.getRule(access, entityId, order);
    }
}
