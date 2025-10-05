package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.tables.MessageWhatsappBusinessAccount.MESSAGE_WHATSAPP_BUSINESS_ACCOUNT;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappBusinessAccountRecord;
import org.springframework.stereotype.Component;

@Component
public class WhatsappBusinessAccountDAO
        extends BaseProviderDAO<MessageWhatsappBusinessAccountRecord, WhatsappBusinessAccount> {

    protected WhatsappBusinessAccountDAO() {
        super(
                WhatsappBusinessAccount.class,
                MESSAGE_WHATSAPP_BUSINESS_ACCOUNT,
                MESSAGE_WHATSAPP_BUSINESS_ACCOUNT.ID,
                MESSAGE_WHATSAPP_BUSINESS_ACCOUNT.WHATSAPP_BUSINESS_ACCOUNT_ID);
    }
}
