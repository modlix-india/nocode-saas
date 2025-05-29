package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

@Service
public abstract class RuleService<R extends UpdatableRecord<R>, D extends Rule<D>, O extends RuleDAO<R, D>>
        extends BaseService<R, D, O> implements IEntitySeries {

    private static final String DEFAULT_KEY = "default";

    private static final String RULE = "rule";
    protected RuleExecutionService ruleExecutionService;
    private ComplexRuleService complexRuleService;
    private SimpleRuleService simpleRuleService;
    private StageService stageService;

    protected abstract Mono<D> createFromRequest(RuleRequest ruleRequest);

    protected abstract Mono<Identity> getEntityId(RuleRequest ruleRequest);

    public abstract Mono<ULong> getUserAssignment(
            String appCode,
            String clientCode,
            ULong entityId,
            ULong stageId,
            String tokenPrefix,
            ULong userId,
            JsonElement data);

    @Autowired
    private void setComplexRuleService(ComplexRuleService complexRuleService) {
        this.complexRuleService = complexRuleService;
    }

    @Autowired
    private void setSimpleRuleService(SimpleRuleService simpleRuleService) {
        this.simpleRuleService = simpleRuleService;
    }

    @Autowired
    private void setRuleExecutionService(RuleExecutionService ruleExecutionService) {
        this.ruleExecutionService = ruleExecutionService;
    }

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    protected String getCacheName() {
        return RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.RULE;
    }

    @Override
    protected Mono<Boolean> evictCache(D entity) {
        return FlatMapUtil.flatMapMono(
                () -> super.evictCache(entity),
                evicted -> this.evictRulesCache(
                        entity.getAppCode(), entity.getClientCode(), entity.getEntityId(), entity.getStageId()));
    }

    private Mono<Boolean> evictRulesCache(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return this.cacheService.evict(this.getCacheName(), this.getCacheKey(appCode, clientCode, entityId, stageId));
    }

    @Override
    protected Mono<D> updatableEntity(D rule) {
        return super.updatableEntity(rule).flatMap(existing -> {
            if (rule.getOrder() == 0) {
                existing.setOrder(0);
                existing.setIsDefault(Boolean.TRUE);
            }
            if (rule.getOrder() > 0) existing.setOrder(rule.getOrder());

            existing.setBreakAtFirstMatch(rule.isBreakAtFirstMatch());

            if (rule.isComplex()) existing.setComplex(Boolean.TRUE);

            if (rule.isSimple()) existing.setSimple(Boolean.TRUE);

            existing.setUserDistributionType(rule.getUserDistributionType());
            existing.setUserDistribution(rule.getUserDistribution());
            existing.setLastAssignedUserId(rule.getLastAssignedUserId());

            return Mono.just(existing);
        });
    }

    protected Mono<D> getDefault(String appCode, String clientCode, ULong entityId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.readDefaultInternal(appCode, clientCode, entityId),
                this.getCacheKey(appCode, clientCode, entityId, DEFAULT_KEY));
    }

    protected Mono<Map<Integer, D>> getRuleWithOrder(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.getRules(appCode, clientCode, entityId, stageId),
                        this.getDefault(appCode, clientCode, entityId)),
                tuple -> {
                    Map<Integer, D> rulesMap = tuple.getT1().stream()
                            .collect(Collectors.toMap(Rule::getOrder, Function.identity(), (a, b) -> b));

                    rulesMap.put(0, tuple.getT2());

                    return Mono.just(rulesMap);
                });
    }

    protected Mono<List<D>> getRules(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.readInternal(appCode, clientCode, entityId, stageId),
                this.getCacheKey(appCode, clientCode, entityId, stageId));
    }

    private Mono<List<D>> readInternal(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return this.dao.getRules(appCode, clientCode, entityId, stageId).collectList();
    }

    private Mono<D> readDefaultInternal(String appCode, String clientCode, ULong entityId) {
        return this.dao.getDefaultRule(appCode, clientCode, entityId);
    }

    public Mono<D> createDefaultRule(RuleRequest ruleRequest) {
        return this.createInternal(ruleRequest, 0);
    }

    public Mono<Map<Integer, D>> createWithOrder(Map<Integer, RuleRequest> ruleRequests) {

        if (ruleRequests == null || ruleRequests.isEmpty()) return Mono.just(Map.of());

        if (!ruleRequests.containsKey(0))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DEFAULT_RULE_MISSING);

        return Flux.fromIterable(ruleRequests.entrySet())
                .flatMap(entry -> {
                    Integer order = entry.getKey();
                    RuleRequest ruleRequest = entry.getValue();

                    if (ruleRequest.getCondition() == null
                            || ruleRequest.getCondition().isEmpty()) return Flux.empty();

                    return this.createInternal(ruleRequest, order).map(rule -> Map.entry(order, rule));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Map<Integer, D>> updateOrder(Map<Integer, Identity> rules) {

        if (rules == null || rules.isEmpty()) return Mono.just(Map.of());

        return FlatMapUtil.flatMapMono(super::hasAccess, hasAccess -> Flux.fromIterable(rules.entrySet())
                .flatMap(entry -> {
                    Integer order = entry.getKey();
                    Identity identity = entry.getValue();

                    return FlatMapUtil.flatMapMono(
                            () -> this.readIdentity(identity),
                            rule -> {
                                rule.setOrder(order);
                                return update(rule);
                            },
                            (rule, updatedRule) -> this.evictCache(updatedRule).map(evicted -> updatedRule),
                            (rule, updatedRule, evictedRule) -> Mono.just(Map.entry(order, evictedRule)));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Mono<D> createInternal(RuleRequest ruleRequest, Integer order) {

        if (order == 0) ruleRequest.setDefault(Boolean.TRUE).setStageId(null);

        if (order > 0
                && (ruleRequest.getStageId() == null || ruleRequest.getStageId().isNull()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.STAGE_MISSING);

        if (ruleRequest.isDefault()) ruleRequest.setStageId(null);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> ruleRequest.isDefault()
                        ? Mono.just(ruleRequest)
                        : stageService
                                .checkAndUpdateIdentity(ruleRequest.getStageId())
                                .map(ruleRequest::setStageId),
                (hasAccess, cRuleRequest) -> this.getRuleFromRequest(hasAccess.getT1(), cRuleRequest, order),
                (hasAccess, cRuleRequest, rule) -> this.createOrUpdateRule(rule),
                (hasAccess, cRuleRequest, rule, cRule) -> {
                    if (rule.isComplex() && ruleRequest.getCondition() instanceof ComplexCondition complexCondition)
                        return complexRuleService
                                .createForCondition(
                                        cRule.getId(), cRule.getEntitySeries(), hasAccess.getT1(), complexCondition)
                                .map(result -> cRule);

                    if (rule.isSimple() && ruleRequest.getCondition() instanceof FilterCondition filterCondition)
                        return simpleRuleService
                                .createForCondition(
                                        cRule.getId(), cRule.getEntitySeries(), hasAccess.getT1(), filterCondition)
                                .map(result -> cRule);

                    return Mono.just(cRule);
                },
                (hasAccess, cRuleRequest, rule, cRule, conditionRule) ->
                        this.evictCache(cRule).map(evicted -> conditionRule));
    }

    private Mono<D> createOrUpdateRule(D rule) {
        return rule.getId() != null ? this.update(rule) : this.create(rule);
    }

    private Mono<D> getRuleFromRequest(Tuple3<String, String, ULong> access, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.getOrCreateRule(access, ruleRequest, order),
                rule -> this.updateUserDistribution(ruleRequest, rule),
                (rule, uRule) -> {
                    uRule.setAppCode(access.getT1());
                    uRule.setClientCode(access.getT2());
                    uRule.setOrder(order);

                    if (!ruleRequest.isDefault())
                        uRule.setStageId(ruleRequest.getStageId().getULongId());

                    return Mono.just(uRule);
                });
    }

    private Mono<D> getOrCreateRule(Tuple3<String, String, ULong> access, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.readIdentityBasicInternal(ruleRequest.getRuleId())
                        .switchIfEmpty(this.getRule(access, ruleRequest, order))
                        .switchIfEmpty(this.createFromRequest(ruleRequest)),
                rule -> {
                    if (rule.getId() != null)
                        return this.deleteOldRelations(rule)
                                .flatMap(result -> this.updateRuleWithRequest(rule, ruleRequest, order));

                    return Mono.just(rule);
                });
    }

    private Mono<D> updateRuleWithRequest(D rule, RuleRequest ruleRequest, Integer order) {

        if (rule.getOrder() > 0) rule.setOrder(order);

        rule.setBreakAtFirstMatch(ruleRequest.isBreakAtFirstMatch());

        if (ruleRequest.isComplex()) rule.setComplex(Boolean.TRUE);

        if (ruleRequest.isSimple()) rule.setSimple(Boolean.TRUE);

        rule.setUserDistributionType(ruleRequest.getUserDistributionType());
        rule.setUserDistribution(ruleRequest.getUserDistribution());

        return Mono.just(rule);
    }

    private Mono<D> deleteOldRelations(D rule) {
        if (rule.isComplex())
            return this.complexRuleService
                    .deleteRule(rule.getId(), rule.getEntitySeries())
                    .map(result -> rule);

        if (rule.isSimple())
            return this.simpleRuleService
                    .deleteRule(rule.getId(), rule.getEntitySeries())
                    .map(result -> rule);

        return Mono.just(rule);
    }

    private Mono<D> getRule(Tuple3<String, String, ULong> access, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.getEntityId(ruleRequest),
                entityId -> this.dao.getRule(
                        access.getT1(),
                        access.getT2(),
                        entityId.getULongId(),
                        ruleRequest.isDefault()
                                ? null
                                : ruleRequest.getStageId().getULongId(),
                        order));
    }

    private Mono<D> updateUserDistribution(RuleRequest ruleRequest, D rule) {

        DistributionType distributionType = ruleRequest.getUserDistributionType();

        if (distributionType == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.USER_DISTRIBUTION_MISSING);

        if (!ruleRequest.getUserDistribution().isValidForType(distributionType))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.USER_DISTRIBUTION_INVALID,
                    distributionType);

        rule.setUserDistributionType(distributionType);
        rule.setUserDistribution(ruleRequest.getUserDistribution());

        return Mono.just(rule);
    }
}
