package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_PHONE_NUMBER;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappPhoneNumberRecord;
import org.springframework.stereotype.Component;

@Component
public class WhatsappPhoneNumberDAO extends BaseProviderDAO<MessageWhatsappPhoneNumberRecord, WhatsappPhoneNumber> {

    protected WhatsappPhoneNumberDAO() {
        super(
                WhatsappPhoneNumber.class,
                MESSAGE_WHATSAPP_PHONE_NUMBER,
                MESSAGE_WHATSAPP_PHONE_NUMBER.ID,
                MESSAGE_WHATSAPP_PHONE_NUMBER.PHONE_NUMBER_ID);
    }
}
