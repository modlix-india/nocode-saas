package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.model.response.rule.RuleResponse;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        extends BaseService<R, D, O> {

    private static final String DEFAULT_KEY = "default";

    private static final String RULE = "rule";
    protected RuleExecutionService ruleExecutionService;
    protected StageService stageService;
    private ComplexRuleService complexRuleService;
    private SimpleRuleService simpleRuleService;

    protected abstract Mono<D> createFromRequest(RuleRequest ruleRequest);

    protected abstract Mono<Identity> getEntityId(Identity entityId);

    protected abstract String getEntityRefName();

    protected abstract Mono<Set<ULong>> getStageIds(
            String appCode, String clientCode, Identity entityId, List<ULong> stageIds);

    protected abstract Mono<ULong> getStageId(String appCode, String clientCode, Identity entityId, ULong stageId);

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
    protected Mono<Boolean> evictCache(D entity) {
        return FlatMapUtil.flatMapMono(
                () -> super.evictCache(entity),
                evicted -> this.evictRulesCache(
                        entity.getAppCode(), entity.getClientCode(), entity.getEntityId(), entity.getStageId()));
    }

    private Mono<Boolean> evictRulesCache(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return this.cacheService
                .evict(this.getCacheName(), this.getCacheKey(appCode, clientCode, entityId, stageId))
                .flatMap(evicted -> this.evictDefaultCache(appCode, clientCode, entityId));
    }

    private Mono<Boolean> evictDefaultCache(String appCode, String clientCode, ULong entityId) {
        return this.cacheService.evict(
                this.getCacheName(), this.getCacheKey(appCode, clientCode, entityId, DEFAULT_KEY));
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

    protected Mono<RuleResponse<D>> getDefaultResponse(String appCode, String clientCode, ULong entityId) {
        return this.getDefault(appCode, clientCode, entityId).flatMap(rule -> {
            if (rule.isComplex()) {
                return this.complexRuleService
                        .getCondition(rule.getId(), this.getEntitySeries())
                        .map(condition -> new RuleResponse<D>().setRule(rule).setCondition(condition));
            } else if (rule.isSimple()) {
                return this.simpleRuleService
                        .getCondition(rule.getId(), this.getEntitySeries(), false)
                        .map(condition -> new RuleResponse<D>().setRule(rule).setCondition(condition));
            }
            return Mono.just(new RuleResponse<D>().setRule(rule));
        });
    }

    protected Mono<D> getDefault(String appCode, String clientCode, ULong entityId) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.readDefaultInternal(appCode, clientCode, entityId),
                this.getCacheKey(appCode, clientCode, entityId, DEFAULT_KEY));
    }

    public Mono<Map<Integer, RuleResponse<D>>> getRuleResponseWithOrder(Identity entityId, List<ULong> stageIds) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        hasAccess -> this.getEntityId(entityId),
                        (hasAccess, entity) -> this.getRuleResponseWithOrder(
                                hasAccess.getT1().getT1(), hasAccess.getT1().getT2(), entity, stageIds))
                .switchIfEmpty(super.msgService
                        .getMessage(AbstractMessageService.OBJECT_NOT_FOUND, this.getEntityRefName(), entityId)
                        .handle((msg, sink) -> sink.error(new GenericException(HttpStatus.NOT_FOUND, msg))));
    }

    private Mono<Map<Integer, RuleResponse<D>>> getRuleResponseWithOrder(
            String appCode, String clientCode, Identity entityId, List<ULong> stageIds) {
        return FlatMapUtil.flatMapMono(
                () -> this.getStageIds(appCode, clientCode, entityId, stageIds),
                allStages -> Mono.zip(
                        this.getRuleResponses(appCode, clientCode, entityId.getULongId(), allStages),
                        this.getDefaultResponse(appCode, clientCode, entityId.getULongId())),
                (allStages, rules) -> {
                    Map<Integer, RuleResponse<D>> rulesMap = rules.getT1().stream()
                            .collect(Collectors.toMap(r -> r.getRule().getOrder(), Function.identity(), (a, b) -> b));

                    rulesMap.put(0, rules.getT2());

                    return Mono.just(rulesMap);
                });
    }

    public Mono<Map<Integer, D>> getRuleWithOrder(Identity entityId, List<ULong> stageIds) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.getEntityId(entityId),
                (hasAccess, entity) -> this.getRuleWithOrder(
                        hasAccess.getT1().getT1(), hasAccess.getT1().getT2(), entity, stageIds));
    }

    private Mono<Map<Integer, D>> getRuleWithOrder(
            String appCode, String clientCode, Identity entityId, List<ULong> stageIds) {
        return FlatMapUtil.flatMapMono(
                () -> this.getStageIds(appCode, clientCode, entityId, stageIds),
                allStages -> Mono.zip(
                        this.getRules(appCode, clientCode, entityId.getULongId(), allStages),
                        this.getDefault(appCode, clientCode, entityId.getULongId())),
                (allStages, rules) -> {
                    Map<Integer, D> rulesMap = rules.getT1().stream()
                            .collect(Collectors.toMap(Rule::getOrder, Function.identity(), (a, b) -> b));
                    rulesMap.put(0, rules.getT2());

                    return Mono.just(rulesMap);
                });
    }

    protected Mono<Map<Integer, D>> getRuleWithOrder(String appCode, String clientCode, ULong entityId, ULong stageId) {
        return FlatMapUtil.flatMapMono(
                () -> Mono.zip(
                        this.getRules(appCode, clientCode, entityId, stageId),
                        this.getDefault(appCode, clientCode, entityId)),
                rules -> {
                    Map<Integer, D> rulesMap = rules.getT1().stream()
                            .collect(Collectors.toMap(Rule::getOrder, Function.identity(), (a, b) -> b));

                    rulesMap.put(0, rules.getT2());

                    return Mono.just(rulesMap);
                });
    }

    private Mono<List<RuleResponse<D>>> getRuleResponses(
            String appCode, String clientCode, ULong entityId, Set<ULong> stageIds) {
        return Flux.fromIterable(stageIds)
                .flatMap(stageId -> this.getRuleResponses(appCode, clientCode, entityId, stageId))
                .collectList()
                .flatMap(rules -> Mono.just(rules.stream().flatMap(List::stream).toList()));
    }

    private Mono<List<RuleResponse<D>>> getRuleResponses(
            String appCode, String clientCode, ULong entityId, ULong stageId) {
        if (entityId == null || stageId == null) return Mono.empty();

        return this.getRules(appCode, clientCode, entityId, stageId).flatMap(rules -> {
            if (rules.isEmpty()) return Mono.just(List.of());

            return Flux.fromIterable(rules)
                    .flatMap(rule -> {
                        RuleResponse<D> response = new RuleResponse<D>().setRule(rule);

                        if (rule.isComplex()) {
                            return this.complexRuleService
                                    .getCondition(rule.getId(), this.getEntitySeries())
                                    .map(response::setCondition)
                                    .defaultIfEmpty(response);
                        } else if (rule.isSimple()) {
                            return this.simpleRuleService
                                    .getCondition(rule.getId(), this.getEntitySeries(), false)
                                    .map(response::setCondition)
                                    .defaultIfEmpty(response);
                        } else {
                            return Mono.just(response);
                        }
                    })
                    .collectList();
        });
    }

    private Mono<List<D>> getRules(String appCode, String clientCode, ULong entityId, Set<ULong> stageIds) {
        return Flux.fromIterable(stageIds)
                .flatMap(stageId -> this.getRules(appCode, clientCode, entityId, stageId))
                .collectList()
                .flatMap(rules -> Mono.just(rules.stream().flatMap(List::stream).toList()));
    }

    private Mono<List<D>> getRules(String appCode, String clientCode, ULong entityId, ULong stageId) {

        if (entityId == null || stageId == null) return Mono.empty();

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

    public Mono<Map<Integer, D>> createWithOrder(Identity entityId, Map<Integer, RuleRequest> ruleRequests) {

        if (ruleRequests == null || ruleRequests.isEmpty()) return Mono.just(Map.of());

        if (!ruleRequests.containsKey(0))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.DEFAULT_RULE_MISSING);
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.getEntityId(entityId),
                (hasAccess, entity) -> this.getRuleWithOrder(
                                hasAccess.getT1().getT1(), hasAccess.getT1().getT2(), entity, null)
                        .switchIfEmpty(Mono.just(Map.of())),
                (hasAccess, entity, rules) -> {
                    if (rules.isEmpty()) return Mono.just(Boolean.TRUE);

                    return Flux.fromIterable(rules.entrySet())
                            .filter(entry -> !ruleRequests.containsKey(entry.getKey()))
                            .flatMap(entry -> this.deleteRule(entry.getValue()))
                            .then(Mono.just(Boolean.TRUE));
                },
                (hasAccess, entity, rules, deleted) -> Flux.fromIterable(ruleRequests.entrySet())
                        .flatMap(entry -> {
                            Integer order = entry.getKey();
                            RuleRequest ruleRequest = entry.getValue();

                            if (ruleRequest.getCondition() == null
                                    || ruleRequest.getCondition().isEmpty()) return Flux.empty();

                            return this.createInternal(hasAccess.getT1(), entity, ruleRequest, order)
                                    .map(rule -> Map.entry(order, rule));
                        })
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue));
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

    private Mono<D> createInternal(
            Tuple3<String, String, ULong> access, Identity entityId, RuleRequest ruleRequest, Integer order) {

        if (order == 0) ruleRequest.setDefault(Boolean.TRUE).setStageId(null);

        if (order > 0
                && (ruleRequest.getStageId() == null || ruleRequest.getStageId().isNull()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.STAGE_MISSING);

        if (ruleRequest.isDefault()) ruleRequest.setStageId(null);

        return FlatMapUtil.flatMapMono(
                () -> this.checkAndUpdateStage(access, entityId, ruleRequest),
                cRuleRequest -> this.getRuleFromRequest(access, entityId, cRuleRequest, order),
                (cRuleRequest, rule) -> this.createOrUpdateRule(rule),
                (cRuleRequest, rule, cRule) -> {
                    if (rule.isComplex() && ruleRequest.getCondition() instanceof ComplexCondition complexCondition)
                        return complexRuleService
                                .createForCondition(cRule.getId(), cRule.getEntitySeries(), access, complexCondition)
                                .map(result -> cRule);

                    if (rule.isSimple() && ruleRequest.getCondition() instanceof FilterCondition filterCondition)
                        return simpleRuleService
                                .createForCondition(cRule.getId(), cRule.getEntitySeries(), access, filterCondition)
                                .map(result -> cRule);

                    return Mono.just(cRule);
                },
                (cRuleRequest, rule, cRule, conditionRule) ->
                        this.evictCache(cRule).map(evicted -> conditionRule));
    }

    private Mono<RuleRequest> checkAndUpdateStage(
            Tuple3<String, String, ULong> access, Identity entityId, RuleRequest ruleRequest) {
        if (ruleRequest.isDefault()) return Mono.just(ruleRequest);

        if (ruleRequest.getStageId() == null || ruleRequest.getStageId().isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.TEMPLATE_STAGE_MISSING);

        if (ruleRequest.getStageId().isId())
            return this.getStageId(
                            access.getT1(),
                            access.getT2(),
                            entityId,
                            ruleRequest.getStageId().getULongId())
                    .map(stageId -> ruleRequest)
                    .switchIfEmpty(this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            ProcessorMessageResourceService.TEMPLATE_STAGE_INVALID));

        return FlatMapUtil.flatMapMono(
                () -> this.stageService.checkAndUpdateIdentity(ruleRequest.getStageId()),
                stage -> this.getStageId(access.getT1(), access.getT2(), entityId, stage.getULongId())
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.TEMPLATE_STAGE_INVALID)),
                (stage, stageId) -> Mono.just(ruleRequest.setStageId(stage)));
    }

    private Mono<D> createOrUpdateRule(D rule) {
        return rule.getId() != null ? this.update(rule) : this.create(rule);
    }

    private Mono<D> getRuleFromRequest(
            Tuple3<String, String, ULong> access, Identity entityId, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.getOrCreateRule(access, entityId, ruleRequest, order),
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

    private Mono<D> getOrCreateRule(
            Tuple3<String, String, ULong> access, Identity entityId, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.readIdentityBasicInternal(ruleRequest.getRuleId())
                        .switchIfEmpty(this.getRule(access, entityId, ruleRequest, order))
                        .switchIfEmpty(this.createFromRequest(ruleRequest)),
                rule -> {
                    if (rule.getId() != null)
                        return this.deleteOldRelations(rule)
                                .flatMap(result -> this.updateRuleWithRequest(rule, ruleRequest, order));

                    return Mono.just(rule.setEntityId(entityId.getULongId()));
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
                    .deleteRule(rule.getId(), rule.getEntitySeries(), Boolean.FALSE)
                    .map(result -> rule);

        return Mono.just(rule);
    }

    private Mono<D> getRule(
            Tuple3<String, String, ULong> access, Identity entityId, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.getEntityId(entityId),
                entity -> this.dao.getRule(
                        access.getT1(),
                        access.getT2(),
                        entity.getULongId(),
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

    private Mono<Integer> deleteRule(D rule) {
        return FlatMapUtil.flatMapMono(
                () -> this.deleteOldRelations(rule),
                relationsDeleted -> this.delete(rule.getId()),
                (relationsDeleted, deleted) -> this.evictCache(rule).map(evicted -> deleted));
    }
}
