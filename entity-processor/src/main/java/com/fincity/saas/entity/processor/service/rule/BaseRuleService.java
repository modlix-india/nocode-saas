package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.IClassConvertor;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.BaseRuleDAO;
import com.fincity.saas.entity.processor.dao.rule.BaseUserDistributionDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.service.product.ProductService;
import com.fincity.saas.entity.processor.service.product.template.ProductTemplateService;
import java.util.List;
import java.util.Map;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class BaseRuleService<
                R extends UpdatableRecord<R>,
                D extends BaseRuleDto<U, D>,
                O extends BaseRuleDAO<R, U, D>,
                T extends UpdatableRecord<T>,
                U extends BaseUserDistributionDto<U>,
                P extends BaseUserDistributionDAO<T, U>>
        extends BaseUpdatableService<R, D, O> {

    private static final String FETCH_USER_DISTRIBUTIONS = "fetchUserDistributions";

    protected final BaseUserDistributionService<T, U, P> userDistributionService;
    protected TicketCRuleExecutionService ticketCRuleExecutionService;
    protected ProductService productService;
    private ProductTemplateService productTemplateService;

    protected BaseRuleService(BaseUserDistributionService<T, U, P> userDistributionService) {
        this.userDistributionService = userDistributionService;
    }

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
    private void setRuleExecutionService(TicketCRuleExecutionService ticketCRuleExecutionService) {
        this.ticketCRuleExecutionService = ticketCRuleExecutionService;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<D> checkEntity(D entity, ProcessorAccess access) {

	    if (entity.getOrder() == null)
		    return this.msgService.throwMessage(
				    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				    ProcessorMessageResourceService.RULE_ORDER_MISSING);

        if (!entity.isDefault()
                && (entity.getCondition() == null || entity.getCondition().isEmpty()))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.RULE_CONDITION_MISSING,
                    entity.getOrder());

        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.updateProductProductTemplate(access, entity),
                uEntity -> this.dao.getRule(
                        null, access, entity.getProductId(), entity.getProductTemplateId(), entity.getOrder()),
                (uEntity, existing) -> {
                    if (existing != null && !existing.getId().equals(uEntity.getId()))
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
            if (rule.getOrder() == BaseRuleDto.DEFAULT_ORDER) existing.setOrder(0);

            if (rule.getOrder() > BaseRuleDto.DEFAULT_ORDER) existing.setOrder(rule.getOrder());

            existing.setUserDistributionType(rule.getUserDistributionType());
            existing.setLastAssignedUserId(rule.getLastAssignedUserId());
            existing.setCondition(rule.getCondition());

            return Mono.just(existing);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<D> create(D entity) {

        if (entity.areDistributionEmpty()) return super.throwMissingParam(BaseRuleDto.Fields.userDistributions);

        if (!entity.areDistributionValid()) return super.throwInvalidParam(BaseRuleDto.Fields.userDistributions);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.create(access, entity),
                        (access, created) -> this.userDistributionService
                                .createDistributions(access, created.getId(), entity.getUserDistributions())
                                .collectList(),
                        (access, created, userDistributions) ->
                                Mono.just((D) created.setUserDistributions(userDistributions)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseRuleService.create"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<D> update(D entity) {

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.readById(access, entity.getId()),
                        (access, existing) -> super.update(access, (D) entity.setId(existing.getId())),
                        (access, existing, updated) -> entity.areDistributionEmpty()
                                ? this.userDistributionService.getUserDistributions(access, updated.getId())
                                : this.userDistributionService
                                        .updateDistributions(access, entity.getId(), entity.getUserDistributions())
                                        .collectList(),
                        (access, existing, updated, userDistributions) ->
                                Mono.just((D) updated.setUserDistributions(userDistributions)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BaseRuleService.update"));
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
                        (access, entity, rules, deleted) -> this.userDistributionService
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

    @SuppressWarnings("unchecked")
    public Mono<List<D>> fillDetails(List<D> rules, MultiValueMap<String, String> queryParams) {

        boolean fetchUserDistributions = BooleanUtil.safeValueOf(queryParams.getFirst(FETCH_USER_DISTRIBUTIONS));

        Flux<D> userFlux = Flux.fromIterable(rules);

        return FlatMapUtil.flatMapFlux(
                        () -> super.hasAccess().flux(),
                        access -> fetchUserDistributions
                                ? userFlux.flatMap(rule -> this.userDistributionService
                                        .getUserDistributions(access, rule.getId())
                                        .map(userDistributions -> (D) rule.setUserDistributions(userDistributions)))
                                : userFlux)
                .collectList();
    }

    public Mono<List<Map<String, Object>>> fillDetailsEager(
            List<Map<String, Object>> rules, MultiValueMap<String, String> queryParams) {

        boolean fetchUserDistributions = BooleanUtil.safeValueOf(queryParams.getFirst(FETCH_USER_DISTRIBUTIONS));

        Flux<Map<String, Object>> userFlux = Flux.fromIterable(rules);

        return FlatMapUtil.flatMapFlux(
                        () -> super.hasAccess().flux(),
                        access -> fetchUserDistributions
                                ? userFlux.flatMap(rule -> this.userDistributionService
                                        .getUserDistributions(access, (ULong) rule.get(AbstractDTO.Fields.id))
                                        .map(userDistributions -> {
                                            rule.put(
                                                    BaseRuleDto.Fields.userDistributions,
                                                    userDistributions.stream()
                                                            .map(IClassConvertor::toMap)
                                                            .toList());
                                            return rule;
                                        }))
                                : userFlux)
                .collectList();
    }
}
