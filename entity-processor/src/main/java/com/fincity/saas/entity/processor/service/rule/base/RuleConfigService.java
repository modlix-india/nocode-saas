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
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
                (hasAccess, entityId) -> ruleService.create(ruleConfigRequest.getRules()),
                (hasAccess, entityId, rules) -> this.createFromRequest(ruleConfigRequest),
                (hasAccess, entityId, rules, ruleConfig) -> this.createUserDistributions(ruleConfigRequest),
                (hasAccess, entityId, rules, ruleConfig, userDistributions) -> {
                    ruleConfig
                            .setEntityId(entityId)
                            .setRules(rules)
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
                (hasAccess, entityId) -> ruleService.update(ruleConfigRequest.getRules()),
                (hasAccess, entityId, rules) -> this.updateFromRequest(ruleConfigRequest),
                (hasAccess, entityId, rules, ruleConfig) -> this.createUserDistributions(ruleConfigRequest),
                (hasAccess, entityId, rules, ruleConfig, userDistributions) -> {
                    ruleConfig.setUserDistributions(userDistributions).setRules(rules);
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

    private Mono<Map<ULong, UserDistribution>> createUserDistributions(T ruleConfigRequest) {
        DistributionType distributionType = ruleConfigRequest.getUserDistributionType();
        Map<BigInteger, UserDistribution> distributions = ruleConfigRequest.getUserDistributions();

        if (distributionType == null || distributions == null || distributions.isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.USER_DISTRIBUTION_MISSING);

        boolean allValid = distributions.values().stream().allMatch(dist -> dist.isValidForType(distributionType));

        if (!allValid)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.USER_DISTRIBUTION_INVALID);

        return Mono.just(distributions.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> ULong.valueOf(entry.getKey()), Map.Entry::getValue, (v1, v2) -> v1, HashMap::new)));
    }
}
