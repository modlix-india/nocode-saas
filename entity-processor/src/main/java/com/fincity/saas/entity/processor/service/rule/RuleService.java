package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.rule.RuleDAO;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;
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

    private static final String RULE = "rule";
    protected RuleExecutionService ruleExecutionService;
    private ComplexRuleService complexRuleService;
    private SimpleRuleService simpleRuleService;
    private StageService stageService;

    protected abstract Mono<D> createFromRequest(RuleRequest ruleRequest);

    public abstract Mono<ULong> getUserAssignment(
            String appCode, String clientCode, ULong entityId, ULong stageId, String tokenPrefix, JsonElement data);

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
    protected Mono<D> updatableEntity(D rule) {

        return super.updatableEntity(rule).flatMap(existing -> {
            existing.setComplex(rule.isComplex());

            if (!rule.isComplex()) existing.setSimple(rule.isSimple());
            return Mono.just(existing);
        });
    }

    public Mono<Map<Integer, D>> createWithOrder(Map<Integer, RuleRequest> ruleRequests) {

        if (ruleRequests == null || ruleRequests.isEmpty()) return Mono.just(Map.of());

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

    public Mono<Integer> deleteRuleInternal(Identity rule) {
        return super.readIdentityInternal(rule).flatMap(existing -> super.delete(existing.getId()));
    }

    private Mono<Integer> deleteRulesInternal(List<Identity> rules) {

        Map<Boolean, List<Identity>> requestsByType =
                rules.stream().collect(Collectors.partitioningBy(Identity::isCode));

        List<String> codeList =
                requestsByType.get(true).stream().map(Identity::getCode).toList();

        List<ULong> idList = requestsByType.get(false).stream()
                .map(Identity::getId)
                .map(ULongUtil::valueOf)
                .toList();

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
                                .createForCondition(cRule.getId(), complexCondition)
                                .map(result -> cRule);

                    if (rule.isSimple() && ruleRequest.getCondition() instanceof FilterCondition filterCondition)
                        return simpleRuleService
                                .createForCondition(cRule.getId(), filterCondition)
                                .map(result -> cRule);

                    return Mono.just(cRule);
                });
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
