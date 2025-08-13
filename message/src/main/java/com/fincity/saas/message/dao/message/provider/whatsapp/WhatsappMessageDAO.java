package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_MESSAGES;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappMessagesRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import java.time.LocalDateTime;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class WhatsappMessageDAO extends BaseProviderDAO<MessageWhatsappMessagesRecord, WhatsappMessage> {

    protected WhatsappMessageDAO() {
        super(
                WhatsappMessage.class,
                MESSAGE_WHATSAPP_MESSAGES,
                MESSAGE_WHATSAPP_MESSAGES.ID,
                MESSAGE_WHATSAPP_MESSAGES.MESSAGE_ID);
    }

    public Mono<WhatsappMessage> findLastMessageBetweenNumbers(
            MessageAccess access, ULong whatsappPhoneNumberId, String customerPhoneNumber, Integer customerDialCode) {

        return Mono.from(this.dslContext
                        .selectFrom(MESSAGE_WHATSAPP_MESSAGES)
                        .where(MESSAGE_WHATSAPP_MESSAGES
                                .APP_CODE
                                .eq(access.getAppCode())
                                .and(MESSAGE_WHATSAPP_MESSAGES.CLIENT_CODE.eq(access.getClientCode()))
                                .and(MESSAGE_WHATSAPP_MESSAGES.WHATSAPP_PHONE_NUMBER_ID.eq(whatsappPhoneNumberId))
                                .and(MESSAGE_WHATSAPP_MESSAGES
                                        .IS_OUTBOUND
                                        .isTrue()
                                        .and(MESSAGE_WHATSAPP_MESSAGES.TO.eq(customerPhoneNumber))
                                        .and(MESSAGE_WHATSAPP_MESSAGES.TO_DIAL_CODE.eq(customerDialCode.shortValue()))
                                        .or(MESSAGE_WHATSAPP_MESSAGES
                                                .IS_OUTBOUND
                                                .isFalse()
                                                .and(MESSAGE_WHATSAPP_MESSAGES.FROM.eq(customerPhoneNumber))
                                                .and(MESSAGE_WHATSAPP_MESSAGES.FROM_DIAL_CODE.eq(
                                                        customerDialCode.shortValue())))))
                        .orderBy(MESSAGE_WHATSAPP_MESSAGES.CREATED_AT.desc())
                        .limit(1))
                .map(rec -> rec.into(this.pojoClass));
    }

    public Mono<WhatsappMessage> findLastInboundMessageFromCustomer(
            MessageAccess access,
            ULong whatsappPhoneNumberId,
            String customerPhoneNumber,
            Integer customerDialCode,
            LocalDateTime since) {

        return Mono.from(this.dslContext
                        .selectFrom(MESSAGE_WHATSAPP_MESSAGES)
                        .where(MESSAGE_WHATSAPP_MESSAGES
                                .APP_CODE
                                .eq(access.getAppCode())
                                .and(MESSAGE_WHATSAPP_MESSAGES.CLIENT_CODE.eq(access.getClientCode()))
                                .and(MESSAGE_WHATSAPP_MESSAGES.WHATSAPP_PHONE_NUMBER_ID.eq(whatsappPhoneNumberId))
                                .and(MESSAGE_WHATSAPP_MESSAGES.IS_OUTBOUND.isFalse())
                                .and(MESSAGE_WHATSAPP_MESSAGES.FROM.eq(customerPhoneNumber))
                                .and(MESSAGE_WHATSAPP_MESSAGES.FROM_DIAL_CODE.eq(customerDialCode.shortValue()))
                                .and(MESSAGE_WHATSAPP_MESSAGES.CREATED_AT.greaterThan(since)))
                        .orderBy(MESSAGE_WHATSAPP_MESSAGES.CREATED_AT.desc())
                        .limit(1))
                .map(rec -> rec.into(this.pojoClass));
    }
}
