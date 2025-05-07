package com.fincity.saas.entity.processor.service.rule.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.processor.dao.rule.base.RuleConfigDAO;
import com.fincity.saas.entity.processor.dto.rule.base.RuleConfig;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.model.base.Identity;
import com.fincity.saas.entity.processor.model.request.rule.RuleConfigRequest;
import com.fincity.saas.entity.processor.service.base.BaseService;
import com.fincity.saas.entity.processor.service.rule.RuleService;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    protected Mono<D> updatableEntity(D entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setBreakAtFirstMatch(entity.isBreakAtFirstMatch());
            existing.setExecuteOnlyIfAllPreviousMatch(entity.isExecuteOnlyIfAllPreviousMatch());
            existing.setExecuteOnlyIfAllPreviousNotMatch(entity.isExecuteOnlyIfAllPreviousNotMatch());
            existing.setContinueOnNoMatch(entity.isContinueOnNoMatch());
            existing.setRules(entity.getRules());
            return Mono.just(existing);
        });
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
                (hasAccess, entityId, rules, ruleConfig) -> {
                    ruleConfig.setEntityId(entityId);
                    ruleConfig.setRules(rules);
                    ruleConfig.setAppCode(hasAccess.getT1().getT1());
                    ruleConfig.setClientCode(hasAccess.getT1().getT2());

                    if (ruleConfig.getAddedByUserId() == null)
                        ruleConfig.setAddedByUserId(hasAccess.getT1().getT3());

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
                (hasAccess, entityId, rules, ruleConfig) -> {
                    ruleConfig.setRules(rules);
                    return super.create(ruleConfig);
                });
    }

    public Mono<Integer> delete(T ruleConfigRequest) {
        return this.readInternal(ruleConfigRequest.getRuleConfigId())
                .flatMap(ruleConfig -> super.delete(ruleConfig.getId()));
    }

    private Mono<D> updateFromRequest(T ruleConfigRequest) {
        return super.readInternal(ruleConfigRequest.getRuleConfigId())
                .map(existing -> this.updateRuleConfigFromRequest(existing, ruleConfigRequest));
    }

    private Mono<D> createFromRequest(T ruleConfigRequest) {
        return this.createNewInstance()
                .map(ruleConfig -> this.updateRuleConfigFromRequest(ruleConfig, ruleConfigRequest));
    }

    private D updateRuleConfigFromRequest(D ruleConfig, T ruleConfigRequest) {
        ruleConfig.setRuleType(ruleConfigRequest.getRuleType());
        ruleConfig.setBreakAtFirstMatch(ruleConfigRequest.isBreakAtFirstMatch());
        ruleConfig.setExecuteOnlyIfAllPreviousMatch(ruleConfigRequest.isExecuteOnlyIfAllPreviousMatch());
        ruleConfig.setExecuteOnlyIfAllPreviousNotMatch(ruleConfigRequest.isExecuteOnlyIfAllPreviousNotMatch());
        ruleConfig.setContinueOnNoMatch(ruleConfigRequest.isContinueOnNoMatch());
        return ruleConfig;
    }
}
