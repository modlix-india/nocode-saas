package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.TicketDuplicationRuleDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.rule.NoOpUserDistribution;
import com.fincity.saas.entity.processor.dto.rule.TicketDuplicationRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketDuplicationRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.StageService;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketDuplicationRuleService
        extends BaseRuleService<
                EntityProcessorTicketDuplicationRulesRecord,
                TicketDuplicationRule,
                TicketDuplicationRuleDAO,
                NoOpUserDistribution> {

    private static final String TICKET_DUPLICATION_RULE = "ticketDuplicationRule";
    private static final String CONDITION_CACHE = "ticketDuplicationRuleCondition";

    private StageService stageService;

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Override
    protected String getCacheName() {
        return TICKET_DUPLICATION_RULE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_DUPLICATION_RULES;
    }

    @Override
    protected Mono<TicketDuplicationRule> checkEntity(TicketDuplicationRule entity, ProcessorAccess access) {

        if (entity.getSource() == null || entity.getSource().isEmpty())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "Source");

        if (entity.getMaxStageId() == null)
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.STAGE_MISSING);

        return super.checkEntity(entity, access).flatMap(validatedEntity -> this.stageService
                .getStage(access, validatedEntity.getProductTemplateId(), validatedEntity.getMaxStageId())
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.TEMPLATE_STAGE_INVALID,
                        validatedEntity.getMaxStageId(),
                        validatedEntity.getProductTemplateId()))
                .thenReturn(validatedEntity));
    }

    @Override
    protected Mono<TicketDuplicationRule> updatableEntity(TicketDuplicationRule rule) {
        return super.updatableEntity(rule).flatMap(existing -> {
            existing.setSource(rule.getSource());
            existing.setSubSource(rule.getSubSource());
            return Mono.just(existing);
        });
    }

    private String getProductConditionCacheName(
            String appCode, String clientCode, ULong productId, ULong productTemplateId) {
        return super.getCacheName(CONDITION_CACHE, appCode, clientCode, productId, productTemplateId);
    }

    private String getProductTemplateConditionCacheName(String appCode, String clientCode, ULong productTemplateId) {
        return super.getCacheName(CONDITION_CACHE, appCode, clientCode, productTemplateId);
    }

    private Mono<Boolean> evictProductConditionCache(
            String appCode, String clientCode, ULong productId, ULong productTemplateId) {
        return super.cacheService.evictAll(
                this.getProductConditionCacheName(appCode, clientCode, productId, productTemplateId));
    }

    private Mono<Boolean> evictProductTemplateConditionCache(
            String appCode, String clientCode, ULong productTemplateId) {
        return super.cacheService.evictAll(
                this.getProductTemplateConditionCacheName(appCode, clientCode, productTemplateId));
    }

    @Override
    protected Mono<Boolean> evictCache(TicketDuplicationRule entity) {
        Mono<Boolean> productEviction = entity.getProductId() != null
                ? this.evictProductConditionCache(
                        entity.getAppCode(),
                        entity.getClientCode(),
                        entity.getProductId(),
                        entity.getProductTemplateId())
                : Mono.just(Boolean.TRUE);

        return Mono.zip(
                        super.evictCache(entity),
                        productEviction,
                        this.evictProductTemplateConditionCache(
                                entity.getAppCode(), entity.getClientCode(), entity.getProductTemplateId()))
                .map((evicted -> evicted.getT1() && evicted.getT2() && evicted.getT3()));
    }

    public Mono<AbstractCondition> getDuplicateRuleCondition(
            ProcessorAccess access, ULong productId, String source, String subSource) {

        return FlatMapUtil.flatMapMono(
                        () -> super.productService.readById(access, productId),
                        product -> this.getProductDuplicateCondition(
                                        access, product.getId(), product.getProductTemplateId(), source, subSource)
                                .switchIfEmpty(this.getProductTemplateDuplicateCondition(
                                        access, product.getProductTemplateId(), source, subSource)))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketDuplicationRuleService.getDuplicateRuleCondition"));
    }

    private Mono<AbstractCondition> getProductDuplicateCondition(
            ProcessorAccess access, ULong productId, ULong productTemplateId, String source, String subSource) {
        return super.cacheService.cacheEmptyValueOrGet(
                this.getProductConditionCacheName(
                        access.getAppCode(), access.getClientCode(), productId, productTemplateId),
                () -> this.getProductDuplicateConditionInternal(
                        access, productId, productTemplateId, source, subSource),
                super.getCacheKey(
                        access.getAppCode(), access.getClientCode(), productId, productTemplateId, source, subSource));
    }

    private Mono<AbstractCondition> getProductDuplicateConditionInternal(
            ProcessorAccess access, ULong productId, ULong productTemplateId, String source, String subSource) {
        return FlatMapUtil.flatMapMono(
                () -> this.getProductDuplicationRules(access, productId, productTemplateId, source, subSource),
                rules -> {
                    if (rules.isEmpty()) return Mono.empty();

                    return Flux.fromIterable(rules)
                            .flatMap(rule -> this.getRuleCondition(access, rule))
                            .collectList()
                            .map(ComplexCondition::or);
                });
    }

    private Mono<AbstractCondition> getProductTemplateDuplicateCondition(
            ProcessorAccess access, ULong productTemplateId, String source, String subSource) {
        return super.cacheService.cacheEmptyValueOrGet(
                this.getProductTemplateConditionCacheName(
                        access.getAppCode(), access.getClientCode(), productTemplateId),
                () -> this.getProductTemplateDuplicateConditionInternal(access, productTemplateId, source, subSource),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), productTemplateId, source, subSource));
    }

    private Mono<AbstractCondition> getProductTemplateDuplicateConditionInternal(
            ProcessorAccess access, ULong productTemplateId, String source, String subSource) {
        return FlatMapUtil.flatMapMono(
                () -> this.getProductTemplateDuplicationRules(access, productTemplateId, source, subSource), rules -> {
                    if (rules.isEmpty()) return Mono.empty();

                    return Flux.fromIterable(rules)
                            .flatMap(rule -> this.getRuleCondition(access, rule))
                            .collectList()
                            .map(ComplexCondition::or);
                });
    }

    private Mono<AbstractCondition> getRuleCondition(ProcessorAccess access, TicketDuplicationRule rule) {

        return FlatMapUtil.flatMapMono(
                () -> this.stageService.getHigherStages(access, rule.getProductTemplateId(), rule.getMaxStageId()),
                stages -> {
                    if (stages.isEmpty()) return Mono.just(rule.getCondition());

                    AbstractCondition stageCondition = new FilterCondition()
                            .setField(Ticket.Fields.stage)
                            .setOperator(FilterConditionOperator.IN)
                            .setMultiValue(
                                    stages.stream().map(AbstractDTO::getId).toList())
                            .setNegate(Boolean.TRUE);

                    return Mono.just(ComplexCondition.and(rule.getCondition(), stageCondition));
                });
    }

    private Mono<List<TicketDuplicationRule>> getProductDuplicationRules(
            ProcessorAccess access, ULong productId, ULong productTemplateId, String source, String subSource) {
        return this.dao
                .getRule(access, productId, productTemplateId, source, subSource)
                .flatMap(rules -> rules.isEmpty() ? Mono.empty() : Mono.just(rules));
    }

    private Mono<List<TicketDuplicationRule>> getProductTemplateDuplicationRules(
            ProcessorAccess access, ULong productTemplateId, String source, String subSource) {
        return this.dao
                .getRule(access, null, productTemplateId, source, subSource)
                .flatMap(rules -> rules.isEmpty() ? Mono.empty() : Mono.just(rules));
    }
}
