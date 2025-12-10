package com.fincity.saas.entity.processor.service.rule;

import com.fincity.saas.commons.exeception.GenericException;
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
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TicketPeDuplicationRuleService
        extends BaseUpdatableService<
                EntityProcessorTicketPeDuplicationRulesRecord, TicketPeDuplicationRule, TicketPeDuplicationRuleDAO> {

    private static final String TICKET_PE_DUPLICATION_RULE_CACHE = "ticketPeDuplicationRule";

    private static final TicketPeDuplicationRule DEFAULT_RULE = (TicketPeDuplicationRule) new TicketPeDuplicationRule()
            .setPhoneNumberAndEmailType(PhoneNumberAndEmailType.PHONE_NUMBER_ONLY)
            .setActive(Boolean.TRUE)
            .setId(ULong.MIN);

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
}
