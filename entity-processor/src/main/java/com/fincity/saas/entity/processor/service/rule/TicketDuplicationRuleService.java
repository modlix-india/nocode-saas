package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
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
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
                NoOpUserDistribution>
        implements IRepositoryProvider {

    private static final String TICKET_DUPLICATION_RULE = "ticketDuplicationRule";
    private static final String PRODUCT_CONDITION_CACHE = "ticketDuplicationProductRuleCondition";
    private static final String PRODUCT_TEMPLATE_CONDITION_CACHE = "ticketDuplicationProductTemplateRuleCondition";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());
    private final Gson gson;

    private StageService stageService;

    @Autowired
    private void setStageService(StageService stageService) {
        this.stageService = stageService;
    }

    @Autowired
    @Lazy
    private TicketDuplicationRuleService self;

    public TicketDuplicationRuleService(Gson gson) {
        this.gson = gson;
    }

    @PostConstruct
    private void init() {
        this.functions.addAll(super.getCommonFunctions("TicketDuplicationRule", TicketDuplicationRule.class, gson));
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

    private String getProductConditionCacheName(String appCode, String clientCode, ULong productId) {
        return super.getCacheName(PRODUCT_CONDITION_CACHE, appCode, clientCode, productId);
    }

    private String getProductTemplateConditionCacheName(String appCode, String clientCode, ULong productTemplateId) {
        return super.getCacheName(PRODUCT_TEMPLATE_CONDITION_CACHE, appCode, clientCode, productTemplateId);
    }

    private Mono<Boolean> evictProductConditionCache(String appCode, String clientCode, ULong productId) {
        return super.cacheService.evictAll(this.getProductConditionCacheName(appCode, clientCode, productId));
    }

    private Mono<Boolean> evictProductTemplateConditionCache(
            String appCode, String clientCode, ULong productTemplateId) {
        return super.cacheService.evictAll(
                this.getProductTemplateConditionCacheName(appCode, clientCode, productTemplateId));
    }

    @Override
    protected Mono<Boolean> evictCache(TicketDuplicationRule entity) {
        Mono<Boolean> productEviction = entity.getProductId() != null
                ? this.evictProductConditionCache(entity.getAppCode(), entity.getClientCode(), entity.getProductId())
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
                        product -> this.getProductDuplicateCondition(access, product.getId(), source, subSource)
                                .switchIfEmpty(this.getProductTemplateDuplicateCondition(
                                        access, product.getProductTemplateId(), source, subSource)))
                .contextWrite(
                        Context.of(LogUtil.METHOD_NAME, "TicketDuplicationRuleService.getDuplicateRuleCondition"));
    }

    private Mono<AbstractCondition> getProductDuplicateCondition(
            ProcessorAccess access, ULong productId, String source, String subSource) {
        return super.cacheService.cacheEmptyValueOrGet(
                this.getProductConditionCacheName(access.getAppCode(), access.getClientCode(), productId),
                () -> this.getProductDuplicateConditionInternal(access, productId, source, subSource),
                super.getCacheKey(access.getAppCode(), access.getClientCode(), productId, source, subSource));
    }

    private Mono<AbstractCondition> getProductDuplicateConditionInternal(
            ProcessorAccess access, ULong productId, String source, String subSource) {
        return FlatMapUtil.flatMapMono(
                () -> this.getProductDuplicationRules(access, productId, source, subSource), rules -> {
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
                                    stages.stream().map(AbstractDTO::getId).toList());

                    return Mono.just(ComplexCondition.and(rule.getCondition(), stageCondition));
                });
    }

    private Mono<List<TicketDuplicationRule>> getProductDuplicationRules(
            ProcessorAccess access, ULong productId, String source, String subSource) {
        return this.dao
                .getRules(access, productId, null, source, null)
                .flatMap(rules -> this.filterRulesForSubSource(rules, subSource));
    }

    private Mono<List<TicketDuplicationRule>> getProductTemplateDuplicationRules(
            ProcessorAccess access, ULong productTemplateId, String source, String subSource) {
        return this.dao
                .getRules(access, null, productTemplateId, source, null)
                .flatMap(rules -> this.filterRulesForSubSource(rules, subSource));
    }

    private Mono<List<TicketDuplicationRule>> filterRulesForSubSource(
            List<TicketDuplicationRule> rules, String subSource) {

        if (rules == null || rules.isEmpty()) return Mono.empty();

        List<TicketDuplicationRule> exactMatches = new ArrayList<>();
        List<TicketDuplicationRule> nullMatches = new ArrayList<>();

        for (TicketDuplicationRule rule : rules) {
            String ruleSubSource = rule.getSubSource();
            if (ruleSubSource == null) {
                nullMatches.add(rule);
            } else if (subSource != null && subSource.equals(ruleSubSource)) {
                exactMatches.add(rule);
            }
        }

        if (!exactMatches.isEmpty()) return Mono.just(exactMatches);
        return nullMatches.isEmpty() ? Mono.empty() : Mono.just(nullMatches);
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(TicketDuplicationRule.class, classSchema);
    }
}
