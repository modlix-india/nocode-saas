package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_MESSAGES;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappMessagesRecord;
import org.springframework.stereotype.Component;

@Component
public class WhatsappMessageDAO extends BaseProviderDAO<MessageWhatsappMessagesRecord, WhatsappMessage> {

    protected WhatsappMessageDAO() {
        super(
                WhatsappMessage.class,
                MESSAGE_WHATSAPP_MESSAGES,
                MESSAGE_WHATSAPP_MESSAGES.ID,
                MESSAGE_WHATSAPP_MESSAGES.MESSAGE_ID);
    }
}
