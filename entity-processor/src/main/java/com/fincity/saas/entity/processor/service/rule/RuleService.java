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
import java.util.stream.Stream;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
            if (existing.getOrder() != 0) existing.setOrder(rule.getOrder());
            existing.setComplex(rule.isComplex());

            if (!rule.isComplex()) existing.setSimple(rule.isSimple());
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

    private Mono<Integer> deleteRulesInternal(List<Identity> rules) {

        Map<Boolean, List<Identity>> requestsByType =
                rules.stream().collect(Collectors.partitioningBy(Identity::isCode));

        List<String> codeList =
                requestsByType.get(true).stream().map(Identity::getCode).toList();

        List<ULong> idList =
                requestsByType.get(false).stream().map(Identity::getULongId).toList();

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> codeList.isEmpty()
                        ? Mono.just(List.of())
                        : this.readByCodes(codeList).collectList(),
                (hasAccess, codeRules) -> idList.isEmpty()
                        ? Mono.just(List.of())
                        : this.readAllFilter(
                                        new FilterCondition().setField("ID").setMultiValue(idList))
                                .collectList(),
                (hasAccess, codeRules, idRules) -> codeRules.isEmpty() && idRules.isEmpty()
                        ? Mono.just(0)
                        : super.deleteMultiple(Stream.concat(codeRules.stream(), idRules.stream())
                                .toList()));
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
                hasAccess -> this.createFromRequest(ruleRequest),
                (hasAccess, rule) -> stageService.checkAndUpdateIdentity(ruleRequest.getStageId()),
                (hasAccess, rule, stageId) -> this.updateUserDistribution(ruleRequest, rule),
                (hasAccess, rule, stageId, uRule) -> {
                    uRule.setAppCode(hasAccess.getT1().getT1());
                    uRule.setClientCode(hasAccess.getT1().getT2());
                    uRule.setOrder(order);
                    uRule.setStageId(stageId.getULongId());
                    return super.create(uRule);
                },
                (hasAccess, rule, stageId, uRule, cRule) -> {
                    if (rule.isComplex() && ruleRequest.getCondition() instanceof ComplexCondition complexCondition)
                        return complexRuleService
                                .createForCondition(cRule.getId(), cRule.getEntitySeries(), complexCondition)
                                .map(result -> cRule);

                    if (rule.isSimple() && ruleRequest.getCondition() instanceof FilterCondition filterCondition)
                        return simpleRuleService
                                .createForCondition(cRule.getId(), cRule.getEntitySeries(), filterCondition)
                                .map(result -> cRule);

                    return Mono.just(cRule);
                },
                (hasAccess, rule, stageId, uRule, cRule, conditionRule) ->
                        this.evictCache(cRule).map(evicted -> conditionRule));
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
