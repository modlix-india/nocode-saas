package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.dao.rule.base.RuleConfigDAO;
import com.fincity.saas.entity.processor.dto.rule.base.RuleConfig;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.enums.rule.DistributionType;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.common.UserDistribution;
import com.fincity.saas.entity.processor.model.request.rule.RuleConfigRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Service
public abstract class RuleConfigService<
                T extends RuleConfigRequest,
                R extends UpdatableRecord<R>,
                D extends RuleConfig<D>,
                O extends RuleConfigDAO<R, D>>
        extends BaseService<R, D, O> implements IEntitySeries {

    private RuleService ruleService;

    @Lazy
    @Autowired
    private void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    protected abstract Mono<ULong> getEntityId(String appCode, String clientCode, ULong userId, Identity entityId);

    protected abstract Mono<D> createNewInstance();

    @Override
    public Mono<D> create(D entity) {
        return Mono.empty();
    }

    @Override
    public Mono<D> update(D entity) {
        return Mono.empty();
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {
        return Mono.empty();
    }

    public Mono<D> read(Identity identity) {
        return super.readIdentityInternal(identity);
    }

    public Mono<D> create(T ruleConfigRequest) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.getEntityId(
                        hasAccess.getT1().getT1(),
                        hasAccess.getT1().getT2(),
                        hasAccess.getT1().getT3(),
                        ruleConfigRequest.getIdentity()),
                (hasAccess, entityId) -> ruleService.createWithUserDistribution(ruleConfigRequest.getRules()),
                (hasAccess, entityId, rules) -> this.createFromRequest(ruleConfigRequest),
                (hasAccess, entityId, rules, ruleConfig) ->
                        this.createUserDistributions(ruleConfigRequest.getUserDistributionType(), rules),
                (hasAccess, entityId, rules, ruleConfig, userDistributions) -> {
                    ruleConfig
                            .setEntityId(entityId)
                            .setRules(this.getOrderToIdMap(rules))
                            .setUserDistributions(userDistributions)
                            .setAddedByUserId(hasAccess.getT1().getT3())
                            .setAppCode(hasAccess.getT1().getT1())
                            .setClientCode(hasAccess.getT1().getT2());
                    return super.create(ruleConfig);
                });
    }

    public Mono<D> update(T ruleConfigRequest) {
        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                hasAccess -> this.getEntityId(
                        hasAccess.getT1().getT1(),
                        hasAccess.getT1().getT2(),
                        hasAccess.getT1().getT3(),
                        ruleConfigRequest.getIdentity()),
                (hasAccess, entityId) -> ruleService.updateWithUserDistribution(ruleConfigRequest.getRules()),
                (hasAccess, entityId, rules) -> this.updateFromRequest(ruleConfigRequest),
                (hasAccess, entityId, rules, ruleConfig) ->
                        this.createUserDistributions(ruleConfigRequest.getUserDistributionType(), rules),
                (hasAccess, entityId, rules, ruleConfig, userDistributions) -> {
                    ruleConfig.setUserDistributions(userDistributions).setRules(this.getOrderToIdMap(rules));
                    return super.update(ruleConfig);
                },
                (hasAccess, entityId, rules, ruleConfig, userDistributions, updated) ->
                        this.evictCache(updated).map(evicted -> updated));
    }

    private Mono<D> updateFromRequest(T ruleConfigRequest) {
        return super.readIdentityInternal(ruleConfigRequest.getRuleConfigId())
                .map(existing -> this.updateRuleConfigFromRequest(existing, ruleConfigRequest));
    }

    private Mono<D> createFromRequest(T ruleConfigRequest) {
        return this.createNewInstance()
                .map(ruleConfig -> this.updateRuleConfigFromRequest(ruleConfig, ruleConfigRequest));
    }

    private D updateRuleConfigFromRequest(D ruleConfig, T ruleConfigRequest) {

        ruleConfig.setBreakAtFirstMatch(ruleConfigRequest.isBreakAtFirstMatch());
        ruleConfig.setExecuteOnlyIfAllPreviousMatch(ruleConfigRequest.isExecuteOnlyIfAllPreviousMatch());
        ruleConfig.setExecuteOnlyIfAllPreviousNotMatch(ruleConfigRequest.isExecuteOnlyIfAllPreviousNotMatch());
        ruleConfig.setContinueOnNoMatch(ruleConfigRequest.isContinueOnNoMatch());
        ruleConfig.setUserDistributionType(ruleConfigRequest.getUserDistributionType());

        return ruleConfig;
    }

    private Mono<Map<ULong, UserDistribution>> createUserDistributions(
            DistributionType distributionType, Map<ULong, Tuple2<Integer, UserDistribution>> rules) {

        if (distributionType == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.USER_DISTRIBUTION_MISSING);

        Map<ULong, UserDistribution> distributionMap = this.getDistributionMap(rules);

        boolean allValid = distributionMap.values().stream().allMatch(dist -> dist.isValidForType(distributionType));

        if (!allValid)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.USER_DISTRIBUTION_INVALID);

        return Mono.just(distributionMap);
    }

    private Map<Integer, ULong> getOrderToIdMap(Map<ULong, Tuple2<Integer, UserDistribution>> rules) {
        return rules.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getValue().getT1(), Map.Entry::getKey, (v1, v2) -> v1, TreeMap::new));
    }

    private Map<ULong, UserDistribution> getDistributionMap(Map<ULong, Tuple2<Integer, UserDistribution>> rules) {
        return rules.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().getT2(), (v1, v2) -> v1, HashMap::new));
    }
}
