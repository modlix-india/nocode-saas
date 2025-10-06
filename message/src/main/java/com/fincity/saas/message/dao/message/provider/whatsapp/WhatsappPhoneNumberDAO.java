package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_PHONE_NUMBER;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumberRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class WhatsappPhoneNumberDAO extends BaseProviderDAO<MessageWhatsappPhoneNumberRecord, WhatsappPhoneNumber> {

    protected WhatsappPhoneNumberDAO() {
        super(
                WhatsappPhoneNumber.class,
                MESSAGE_WHATSAPP_PHONE_NUMBER,
                MESSAGE_WHATSAPP_PHONE_NUMBER.ID,
                MESSAGE_WHATSAPP_PHONE_NUMBER.PHONE_NUMBER_ID);
    }

    public Mono<WhatsappPhoneNumber> getByPhoneNumberId(MessageAccess messageAccess, String phoneNumberId) {

        if (phoneNumberId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> super.messageAccessCondition(
                        FilterCondition.make(WhatsappPhoneNumber.Fields.phoneNumberId, phoneNumberId)
                                .setOperator(FilterConditionOperator.EQUALS),
                        messageAccess),
                super::filter,
                (messageAccessCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<WhatsappPhoneNumber> getByAccountId(MessageAccess messageAccess, ULong whatsappBusinessAccountId) {

        if (whatsappBusinessAccountId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> super.messageAccessCondition(
                        ComplexCondition.and(
                                FilterCondition.make(
                                                WhatsappPhoneNumber.Fields.whatsappBusinessAccountId,
                                                whatsappBusinessAccountId)
                                        .setOperator(FilterConditionOperator.EQUALS),
                                FilterCondition.make(WhatsappPhoneNumber.Fields.isDefault, Boolean.TRUE)
                                        .setOperator(FilterConditionOperator.IS_TRUE)),
                        messageAccess),
                super::filter,
                (messageAccessCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<WhatsappPhoneNumber> getByAccountAndPhoneNumberId(
            MessageAccess messageAccess, ULong whatsappBusinessAccountId, String phoneNumberId) {

        if (whatsappBusinessAccountId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> super.messageAccessCondition(
                        ComplexCondition.and(
                                FilterCondition.make(
                                                WhatsappPhoneNumber.Fields.whatsappBusinessAccountId,
                                                whatsappBusinessAccountId)
                                        .setOperator(FilterConditionOperator.EQUALS),
                                FilterCondition.make(WhatsappPhoneNumber.Fields.phoneNumberId, phoneNumberId)
                                        .setOperator(FilterConditionOperator.EQUALS)),
                        messageAccess),
                super::filter,
                (messageAccessCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<WhatsappPhoneNumber> getDefaultPhoneNumber(MessageAccess messageAccess) {
        return FlatMapUtil.flatMapMono(
                () -> super.messageAccessCondition(
                        FilterCondition.make(WhatsappPhoneNumber.Fields.isDefault, Boolean.TRUE)
                                .setOperator(FilterConditionOperator.IS_TRUE),
                        messageAccess),
                super::filter,
                (messageAccessCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }
}
