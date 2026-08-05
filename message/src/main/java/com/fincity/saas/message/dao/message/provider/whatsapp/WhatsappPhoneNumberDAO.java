package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_PHONE_NUMBERS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumbersRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class WhatsappPhoneNumberDAO extends BaseProviderDAO<MessageWhatsappPhoneNumbersRecord, WhatsappPhoneNumber> {

    protected WhatsappPhoneNumberDAO() {
        super(
                WhatsappPhoneNumber.class,
                MESSAGE_WHATSAPP_PHONE_NUMBERS,
                MESSAGE_WHATSAPP_PHONE_NUMBERS.ID,
                MESSAGE_WHATSAPP_PHONE_NUMBERS.PHONE_NUMBER_ID);
    }

    /**
     * The owning row for a Meta phone number id, with no tenant filter, because the caller is
     * asking precisely so it can learn the tenant.
     *
     * <p>Safe to leave unscoped: {@code PHONE_NUMBER_ID} carries a unique key of its own
     * ({@code UK2_WHATSAPP_PHONE_NUMBER_PHONE_NUMBER_ID}, on that column alone rather than scoped
     * by app and client), so this can match at most one row. The id is a Meta object id, which is
     * globally unique on their side too.
     *
     * <p>Used only by the inbound webhook, which arrives on one URL for the whole platform and has
     * nothing but the payload to tell it whose message this is. Every other read goes through the
     * access-scoped overload; this one must not become a general-purpose lookup.
     */
    public Mono<WhatsappPhoneNumber> getByPhoneNumberIdInternal(String phoneNumberId) {

        if (phoneNumberId == null || phoneNumberId.isBlank()) return Mono.empty();

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(MESSAGE_WHATSAPP_PHONE_NUMBERS.PHONE_NUMBER_ID.eq(phoneNumberId)))
                .map(e -> e.into(this.pojoClass));
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

    /**
     * The default number of one business account, not of the tenant.
     *
     * <p>The account filter is what makes the answer usable. A tenant running two WABAs has a
     * default on each, and without it whichever row the database returned first would win: a send
     * that falls back to the default could go out under a business identity the customer has never
     * contacted, and their reply would arrive on an account nobody is watching. "Default" is only
     * meaningful inside the account whose numbers it is chosen from.
     */
    public Mono<WhatsappPhoneNumber> getDefaultPhoneNumber(
            MessageAccess messageAccess, ULong whatsappBusinessAccountId) {

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

    /**
     * The tenant's default number without regard to which business account it sits on.
     *
     * <p>Only for callers that genuinely have no account in hand, which today means the internal
     * cross-service read where the other side knows a tenant and nothing more. Never use it to pick
     * the number a message goes out on: with two WABAs it answers from whichever the database
     * reaches first, and that is the wrong business identity half the time. Use the account-scoped
     * overload there.
     */
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

    public Mono<WhatsappPhoneNumber> getByProductId(MessageAccess messageAccess, ULong productId) {

        if (productId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> super.messageAccessCondition(
                        FilterCondition.make(WhatsappPhoneNumber.Fields.productId, productId)
                                .setOperator(FilterConditionOperator.EQUALS),
                        messageAccess),
                super::filter,
                (messageAccessCondition, jCondition) -> Mono.from(
                                this.dslContext.selectFrom(this.table).where(jCondition))
                        .map(e -> e.into(this.pojoClass)));
    }
}
