package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.model.response.rule.RuleResponse;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
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

@Service
public abstract class RuleService<R extends UpdatableRecord<R>, D extends Rule<D>, O extends RuleDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    private static final String DEFAULT_KEY = "default";

    private static final String RULE = "rule";
    protected RuleExecutionService ruleExecutionService;
    protected StageService stageService;
    private ComplexRuleService complexRuleService;
    private SimpleRuleService simpleRuleService;

    protected abstract Mono<D> createFromRequest(RuleRequest ruleRequest);

    protected abstract Mono<Identity> getEntityId(ProcessorAccess access, Identity entityId);

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
        return this.getDefault(appCode, clientCode, entityId)
                .flatMap(rule -> {
                    if (rule.isComplex()) {
                        return this.complexRuleService
                                .getCondition(rule.getId(), this.getEntitySeries())
                                .map(condition ->
                                        new RuleResponse<D>().setRule(rule).setCondition(condition));
                    } else if (rule.isSimple()) {
                        return this.simpleRuleService
                                .getCondition(rule.getId(), this.getEntitySeries(), false)
                                .map(condition ->
                                        new RuleResponse<D>().setRule(rule).setCondition(condition));
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
                        access -> this.getEntityId(access, entityId),
                        (access, entity) -> this.getRuleResponseWithOrder(
                                access.getAppCode(), access.getClientCode(), entity, stageIds))
                .switchIfEmpty(Mono.just(Map.of()));
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
                access -> this.getEntityId(access, entityId),
                (access, entity) ->
                        this.getRuleWithOrder(access.getAppCode(), access.getClientCode(), entity, stageIds));
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
                access -> this.getEntityId(access, entityId),
                (access, entity) -> this.getRuleWithOrder(access.getAppCode(), access.getClientCode(), entity, null)
                        .switchIfEmpty(Mono.just(Map.of())),
                (access, entity, rules) -> {
                    if (rules.isEmpty()) return Mono.just(Boolean.TRUE);

                    return Flux.fromIterable(rules.entrySet())
                            .filter(entry -> !ruleRequests.containsKey(entry.getKey()))
                            .flatMap(entry -> this.deleteRule(entry.getValue()))
                            .then(Mono.just(Boolean.TRUE));
                },
                (access, entity, rules, deleted) -> Flux.fromIterable(ruleRequests.entrySet())
                        .flatMap(entry -> {
                            Integer order = entry.getKey();
                            RuleRequest ruleRequest = entry.getValue();

                            if (order == 0)
                                ruleRequest.getRule().setDefault(Boolean.TRUE).setStageId(null);

                            if (ruleRequest.getCondition() == null
                                    || ruleRequest.getCondition().isEmpty())
                                return order == 0
                                        ? this.handleDefaultRuleNullCondition(access, entity, ruleRequest, order)
                                        : Flux.empty();

                            return this.createInternal(access, entity, ruleRequest, order)
                                    .map(rule -> Map.entry(order, rule));
                        })
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Mono<Map<Integer, D>> updateOrder(Map<Integer, Identity> rules) {

        if (rules == null || rules.isEmpty()) return Mono.just(Map.of());

        return FlatMapUtil.flatMapMono(super::hasAccess, access -> Flux.fromIterable(rules.entrySet())
                .flatMap(entry -> {
                    Integer order = entry.getKey();
                    Identity identity = entry.getValue();

                    return FlatMapUtil.flatMapMono(
                            () -> this.readIdentityWithAccess(identity),
                            rule -> {
                                rule.setOrder(order);
                                return update(rule);
                            },
                            (rule, updatedRule) -> this.evictCache(updatedRule).map(evicted -> updatedRule),
                            (rule, updatedRule, evictedRule) -> Mono.just(Map.entry(order, evictedRule)));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Mono<D> createInternal(ProcessorAccess access, Identity entityId, RuleRequest ruleRequest, Integer order) {

        if (order > 0
                && (ruleRequest.getRule().getStageId() == null
                        || ruleRequest.getRule().getStageId().isNull()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.STAGE_MISSING);

        if (ruleRequest.getRule().isDefault()) ruleRequest.getRule().setStageId(null);

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

    private Mono<RuleRequest> checkAndUpdateStage(ProcessorAccess access, Identity entityId, RuleRequest ruleRequest) {

        if (ruleRequest.getRule().isDefault()) return Mono.just(ruleRequest);

        if (ruleRequest.getRule().getStageId() == null
                || ruleRequest.getRule().getStageId().isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.TEMPLATE_STAGE_MISSING);

        if (ruleRequest.getRule().getStageId().isId())
            return this.getStageId(
                            access.getAppCode(),
                            access.getClientCode(),
                            entityId,
                            ruleRequest.getRule().getStageId().getULongId())
                    .map(stageId -> ruleRequest)
                    .switchIfEmpty(this.msgService.throwMessage(
                            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                            ProcessorMessageResourceService.TEMPLATE_STAGE_INVALID));

        return FlatMapUtil.flatMapMono(
                () -> this.stageService.checkAndUpdateIdentityWithAccess(
                        access, ruleRequest.getRule().getStageId()),
                stage -> this.getStageId(access.getAppCode(), access.getClientCode(), entityId, stage.getULongId())
                        .switchIfEmpty(this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.TEMPLATE_STAGE_INVALID)),
                (stage, stageId) ->
                        Mono.just(ruleRequest.getRule().setStageId(stage)).map(r -> ruleRequest));
    }

    private Mono<D> createOrUpdateRule(D rule) {
        return rule.getId() != null ? this.update(rule) : this.create(rule);
    }

    private Mono<D> getRuleFromRequest(
            ProcessorAccess access, Identity entityId, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.getOrCreateRule(access, entityId, ruleRequest, order),
                rule -> this.updateUserDistribution(ruleRequest, rule),
                (rule, uRule) -> {
                    uRule.setAppCode(access.getAppCode());
                    uRule.setClientCode(access.getClientCode());
                    uRule.setOrder(order);

                    if (!ruleRequest.getRule().isDefault())
                        uRule.setStageId(ruleRequest.getRule().getStageId().getULongId());

                    return Mono.just(uRule);
                });
    }

    private Mono<D> getOrCreateRule(ProcessorAccess access, Identity entityId, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.readIdentityWithAccessEmpty(
                                access, ruleRequest.getRule().getId())
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

        rule.setBreakAtFirstMatch(ruleRequest.getRule().isBreakAtFirstMatch());

        if (ruleRequest.isComplex()) rule.setComplex(Boolean.TRUE);

        if (ruleRequest.isSimple()) rule.setSimple(Boolean.TRUE);

        rule.setUserDistributionType(ruleRequest.getRule().getUserDistributionType());
        rule.setUserDistribution(ruleRequest.getRule().getUserDistribution());

        if (ruleRequest.getRule().getDescription() != null)
            rule.setDescription(ruleRequest.getRule().getDescription());

        if (ruleRequest.getRule().getName() != null)
            rule.setName(ruleRequest.getRule().getName());

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

    private Mono<D> getRule(ProcessorAccess access, Identity entityId, RuleRequest ruleRequest, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.getEntityId(access, entityId),
                entity -> this.dao.getRule(
                        access.getAppCode(),
                        access.getClientCode(),
                        entity.getULongId(),
                        ruleRequest.getRule().isDefault()
                                ? null
                                : ruleRequest.getRule().getStageId().getULongId(),
                        order));
    }

    private Mono<D> updateUserDistribution(RuleRequest ruleRequest, D rule) {

        DistributionType distributionType = ruleRequest.getRule().getUserDistributionType();

        if (distributionType == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.USER_DISTRIBUTION_TYPE_MISSING);

        if (!ruleRequest.getRule().getUserDistribution().isValidForType(distributionType))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.USER_DISTRIBUTION_INVALID,
                    distributionType);

        rule.setUserDistributionType(distributionType);
        rule.setUserDistribution(ruleRequest.getRule().getUserDistribution().transformToValid());

        return Mono.just(rule);
    }

    private Mono<Integer> deleteRule(D rule) {
        return FlatMapUtil.flatMapMono(
                () -> this.deleteOldRelations(rule),
                relationsDeleted -> this.delete(rule.getId()),
                (relationsDeleted, deleted) -> this.evictCache(rule).map(evicted -> deleted));
    }

    private Flux<Map.Entry<Integer, D>> handleDefaultRuleNullCondition(
            ProcessorAccess access, Identity entity, RuleRequest ruleRequest, Integer order) {

        return FlatMapUtil.flatMapMono(
                        () -> this.getRule(access, entity, ruleRequest, order),
                        this::deleteOldRelations,
                        (dRule, relationsDeleted) -> this.evictCache(dRule).map(evicted -> dRule),
                        (dRule, relationsDeleted, evictedRule) -> this.createInternal(
                                        access, entity, ruleRequest, order)
                                .map(rule -> Map.entry(order, rule)))
                .switchIfEmpty(
                        this.createInternal(access, entity, ruleRequest, order).map(rule -> Map.entry(order, rule)))
                .flux();
    }
}
