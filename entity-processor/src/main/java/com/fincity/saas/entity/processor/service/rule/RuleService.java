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
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorRulesRecord;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.model.response.ProcessorResponse;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class RuleService extends BaseService<EntityProcessorRulesRecord, Rule, RuleDAO> implements IEntitySeries {

    private static final String RULE = "rule";

    private final ComplexRuleService complexRuleService;
    private final SimpleRuleService simpleRuleService;

    public RuleService(ComplexRuleService complexRuleService, SimpleRuleService simpleRuleService) {
        this.complexRuleService = complexRuleService;
        this.simpleRuleService = simpleRuleService;
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
    protected Mono<Rule> updatableEntity(Rule rule) {

        return super.updatableEntity(rule).flatMap(existing -> {
            existing.setComplex(rule.isComplex());

            if (!rule.isComplex()) existing.setSimple(rule.isSimple());
            return Mono.just(existing);
        });
    }

    public Mono<ProcessorResponse> create(RuleRequest ruleRequest) {

        if (ruleRequest.getCondition() == null || ruleRequest.getCondition().isEmpty()) return Mono.empty();

        return this.createInternal(ruleRequest)
                .map(result -> ProcessorResponse.ofCreated(result.getCode(), this.getEntitySeries()));
    }

    public Mono<Map<ULong, Integer>> createWithOrder(Map<Integer, RuleRequest> ruleRequests) {

        if (ruleRequests == null || ruleRequests.isEmpty()) return Mono.just(Map.of());

        return Flux.fromIterable(ruleRequests.entrySet())
                .flatMap(entry -> {
                    Integer order = entry.getKey();
                    RuleRequest ruleRequest = entry.getValue();

                    if (ruleRequest.getCondition() == null
                            || ruleRequest.getCondition().isEmpty()) return Flux.empty();

                    return this.createInternal(ruleRequest).map(rule -> Map.entry(rule.getId(), order));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Map<ULong, Tuple2<Integer, UserDistribution>>> createWithUserDistribution(
            Map<Integer, RuleRequest> ruleRequests) {

        if (ruleRequests == null || ruleRequests.isEmpty()) return Mono.just(Map.of());

        return Flux.fromIterable(ruleRequests.entrySet())
                .flatMap(entry -> {
                    Integer order = entry.getKey();
                    UserDistribution userDistribution = entry.getValue().getUserDistribution();

                    if (userDistribution == null)
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.USER_DISTRIBUTION_MISSING,
                                entry.getValue().getRuleId());

                    RuleRequest ruleRequest = entry.getValue();

                    if (ruleRequest.getCondition() == null
                            || ruleRequest.getCondition().isEmpty()) return Flux.empty();

                    return this.createInternal(ruleRequest)
                            .map(rule -> Map.entry(rule.getId(), Tuples.of(order, userDistribution)));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Map<ULong, Integer>> updateWithOrder(Map<Integer, RuleRequest> ruleRequests) {

        if (ruleRequests == null || ruleRequests.isEmpty()) return Mono.just(Map.of());

        return FlatMapUtil.flatMapMono(
                () -> this.deleteRulesInternal(ruleRequests.values().stream()
                        .map(RuleRequest::getRuleId)
                        .toList()),
                deleted -> this.createWithOrder(ruleRequests));
    }

    public Mono<Map<ULong, Tuple2<Integer, UserDistribution>>> updateWithUserDistribution(
            Map<Integer, RuleRequest> ruleRequests) {

        if (ruleRequests == null || ruleRequests.isEmpty()) return Mono.just(Map.of());

        return FlatMapUtil.flatMapMono(
                () -> this.deleteRulesInternal(ruleRequests.values().stream()
                        .map(RuleRequest::getRuleId)
                        .toList()),
                deleted -> this.createWithUserDistribution(ruleRequests));
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

    private Mono<Rule> createInternal(RuleRequest ruleRequest) {

        Rule rule = Rule.of(ruleRequest);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> {
                    rule.setAppCode(hasAccess.getT1().getT1());
                    rule.setClientCode(hasAccess.getT1().getT2());

                    if (rule.getAddedByUserId() == null)
                        rule.setAddedByUserId(hasAccess.getT1().getT3());

                    return super.create(rule);
                },
                (hasAccess, cRule) -> {
                    if (rule.isComplex() && ruleRequest.getCondition() instanceof ComplexCondition complexCondition)
                        return complexRuleService
                                .createForCondition(cRule, complexCondition)
                                .map(result -> cRule);

                    if (rule.isSimple() && ruleRequest.getCondition() instanceof FilterCondition filterCondition)
                        return simpleRuleService
                                .createForCondition(cRule, filterCondition)
                                .map(result -> cRule);

                    return Mono.just(cRule);
                });
    }
}
