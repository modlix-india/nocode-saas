package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.BaseUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.rule.RuleRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
import java.util.List;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class BaseRuleService<
                R extends UpdatableRecord<R>, D extends BaseRuleDto<D>, O extends BaseRuleDAO<R, D>>
        extends BaseUpdatableService<R, D, O> {

    protected RuleExecutionService ruleExecutionService;
    protected ProductService productService;
    private ProductTemplateService productTemplateService;

    @Lazy
    @Autowired
    private void setValueTemplateService(ProductTemplateService productTemplateService) {
        this.productTemplateService = productTemplateService;
    }

    @Lazy
    @Autowired
    private void setProductService(ProductService productService) {
        this.productService = productService;
    }

    @Autowired
    private void setRuleExecutionService(RuleExecutionService ruleExecutionService) {
        this.ruleExecutionService = ruleExecutionService;
    }

    protected abstract <
                    S extends UpdatableRecord<S>,
                    E extends BaseUserDistributionDto<E>,
                    P extends BaseUserDistributionDAO<S, E>>
            BaseUserDistributionService<S, E, P> getUserDistributionService();

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<D> checkEntity(D entity, ProcessorAccess access) {

        if (entity.getCondition() == null || entity.getCondition().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.RULE_CONDITION_MISSING,
                    entity.getOrder());

        if (entity.getOrder() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.RULE_ORDER_MISSING);

        if (entity.getProductId() == null && entity.getProductTemplateId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.RULE_PRODUCT_MISSING);

        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.updateProductProductTemplate(access, entity),
                uEntity -> this.dao.getRule(
                        null, access, entity.getProductId(), entity.getProductTemplateId(), entity.getOrder()),
                (uEntity, existing) -> {
                    if (existing != null)
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                ProcessorMessageResourceService.DUPLICATE_RULE_ORDER,
                                existing.getId(),
                                entity.getOrder());

                    return Mono.just(uEntity);
                });
    }

    @SuppressWarnings("unchecked")
    private Mono<D> updateProductProductTemplate(ProcessorAccess access, D entity) {
        if (entity.getProductId() == null)
            return this.productTemplateService
                    .readById(access, entity.getProductTemplateId())
                    .map(template -> (D) entity.setProductTemplateId(template.getId()));

        return FlatMapUtil.flatMapMono(() -> this.productService.readById(access, entity.getProductId()), product -> {
            if (product.getProductTemplateId() == null)
                return this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.PRODUCT_TEMPLATE_MISSING,
                        product.getId());

            return Mono.just(
                    (D) entity.setProductId(product.getId()).setProductTemplateId(product.getProductTemplateId()));
        });
    }

    @Override
    protected Mono<D> updatableEntity(D rule) {
        return super.updatableEntity(rule).flatMap(existing -> {
            existing.setProductId(rule.getProductId());
            existing.setProductTemplateId(rule.getProductTemplateId());

            if (rule.getOrder() == BaseRuleDto.DEFAULT_ORDER) existing.setOrder(0);

            if (rule.getOrder() > BaseRuleDto.DEFAULT_ORDER) existing.setOrder(rule.getOrder());

            existing.setUserDistributionType(rule.getUserDistributionType());
            existing.setCondition(rule.getCondition());

            return Mono.just(existing);
        });
    }

    public Mono<D> createWithDistribution(RuleRequest<D> ruleRequest) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.create(access, ruleRequest.getEntity()),
                        (access, entity) -> this.getUserDistributionService()
                                .createDistributions(
                                        access,
                                        entity.getId(),
                                        ruleRequest.getUserId(),
                                        ruleRequest.getRoleId(),
                                        ruleRequest.getProfileId(),
                                        ruleRequest.getDesignationId())
                                .then(Mono.just(entity)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseRuleService.createWithDistribution"));
    }

    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readById(access, id),
                        (access, entity) ->
                                this.dao.getRules(null, access, entity.getProductId(), entity.getProductTemplateId()),
                        (access, entity, rules) -> this.shiftOrdersAndUpdate(access, entity, rules)
                                .then(super.deleteInternal(access, entity)),
                        (access, entity, rules, deleted) -> this.getUserDistributionService()
                                .deleteByRuleId(access, id)
                                .then(Mono.just(deleted)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseRuleService.delete"));
    }

    private Mono<Void> shiftOrdersAndUpdate(ProcessorAccess access, D entity, List<D> rules) {
        return Flux.fromIterable(rules)
                .filter(rule -> rule.getOrder() > entity.getOrder())
                .flatMap(rule -> {
                    rule.setOrder(rule.getOrder() - 1);
                    return super.updateInternal(access, rule);
                })
                .then();
    }
}
