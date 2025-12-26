package com.fincity.saas.entity.processor.service.rule;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.functions.AbstractProcessorFunction;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.rule.TicketPeDuplicationRuleDAO;
import com.fincity.saas.entity.processor.dto.rule.TicketPeDuplicationRule;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.PhoneNumberAndEmailType;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketPeDuplicationRulesRecord;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketPeDuplicationRuleService
        extends BaseUpdatableService<
                EntityProcessorTicketPeDuplicationRulesRecord, TicketPeDuplicationRule, TicketPeDuplicationRuleDAO>
        implements IRepositoryProvider {

    private static final String TICKET_PE_DUPLICATION_RULE_CACHE = "ticketPeDuplicationRule";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());
    private final Gson gson;

    @Autowired
    @Lazy
    private TicketPeDuplicationRuleService self;

    private static final TicketPeDuplicationRule DEFAULT_RULE = (TicketPeDuplicationRule) new TicketPeDuplicationRule()
            .setPhoneNumberAndEmailType(PhoneNumberAndEmailType.PHONE_NUMBER_ONLY)
            .setActive(Boolean.TRUE)
            .setId(ULong.MIN);

    public TicketPeDuplicationRuleService(Gson gson) {
        this.gson = gson;
    }

    @PostConstruct
    private void init() {

        this.functions.addAll(super.getCommonFunctions("TicketPeDuplicationRule", TicketPeDuplicationRule.class, gson));

        this.functions.add(AbstractProcessorFunction.createServiceFunction(
                "TicketPeDuplicationRule",
                "GetLoggedInRule",
                "result",
                Schema.ofRef("EntityProcessor.DTO.Rule.TicketPeDuplicationRule"),
                gson,
                self::getLoggedInRule));
    }

    @Override
    protected String getCacheName() {
        return TICKET_PE_DUPLICATION_RULE_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TICKET_PE_DUPLICATION_RULES;
    }

    @Override
    protected Mono<Boolean> evictCache(TicketPeDuplicationRule entity) {
        return Mono.zip(
                super.evictCache(entity),
                super.cacheService.evict(
                        this.getCacheName(), super.getCacheKey("rule", entity.getAppCode(), entity.getClientCode())),
                (baseEvicted, ruleEvicted) -> baseEvicted && ruleEvicted);
    }

    @Override
    protected Mono<TicketPeDuplicationRule> updatableEntity(TicketPeDuplicationRule entity) {
        return super.updatableEntity(entity)
                .flatMap(existing -> {
                    existing.setPhoneNumberAndEmailType(entity.getPhoneNumberAndEmailType());
                    return Mono.just(existing);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketPeDuplicationRuleService.updatableEntity"));
    }

    @Override
    protected Mono<TicketPeDuplicationRule> checkEntity(TicketPeDuplicationRule entity, ProcessorAccess access) {

        if (entity.getPhoneNumberAndEmailType() == null)
            entity.setPhoneNumberAndEmailType(DEFAULT_RULE.getPhoneNumberAndEmailType());

        if (entity.getId() != null) return Mono.just(entity);

        return this.dao
                .readByAppCodeAndClientCode(access)
                .flatMap(existing -> this.msgService.<TicketPeDuplicationRule>throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        ProcessorMessageResourceService.DUPLICATE_ENTITY,
                        this.getEntityName(),
                        access.getAppCode(),
                        access.getEffectiveClientCode()))
                .switchIfEmpty(Mono.just(entity));
    }

    @Override
    protected Mono<TicketPeDuplicationRule> create(ProcessorAccess access, TicketPeDuplicationRule entity) {
        return super.create(access, entity)
                .flatMap(created -> this.evictCache(created).map(evicted -> created));
    }

    public Mono<AbstractCondition> getTicketCondition(ProcessorAccess access, PhoneNumber phoneNumber, Email email) {
        return this.getRule(access)
                .map(rule -> rule.getPhoneNumberAndEmailType().getTicketCondition(phoneNumber, email))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketPeDuplicationRuleService.getCondition"));
    }

    public Mono<TicketPeDuplicationRule> getLoggedInRule() {
        return super.hasAccess()
                .flatMap(this::getRule)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TicketPeDuplicationRuleService.getLoggedInRule"));
    }

    private Mono<TicketPeDuplicationRule> getRule(ProcessorAccess access) {
        return this.getRuleInternal(access).switchIfEmpty(Mono.just(DEFAULT_RULE));
    }

    private Mono<TicketPeDuplicationRule> getRuleInternal(ProcessorAccess access) {
        return super.cacheService.cacheEmptyValueOrGet(
                this.getCacheName(),
                () -> this.dao.readByAppCodeAndClientCode(access),
                super.getCacheKey("rule", access.getAppCode(), access.getClientCode()));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(TicketPeDuplicationRule.class, classSchema);
    }
}
