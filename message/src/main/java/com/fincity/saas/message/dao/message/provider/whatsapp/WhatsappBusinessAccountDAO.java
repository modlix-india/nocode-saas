package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.tables.MessageWhatsappBusinessAccounts.MESSAGE_WHATSAPP_BUSINESS_ACCOUNTS;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappBusinessAccountsRecord;
import org.springframework.stereotype.Component;

@Component
public class WhatsappBusinessAccountDAO
        extends BaseProviderDAO<MessageWhatsappBusinessAccountsRecord, WhatsappBusinessAccount> {

    protected WhatsappBusinessAccountDAO() {
        super(
                WhatsappBusinessAccount.class,
                MESSAGE_WHATSAPP_BUSINESS_ACCOUNTS,
                MESSAGE_WHATSAPP_BUSINESS_ACCOUNTS.ID,
                MESSAGE_WHATSAPP_BUSINESS_ACCOUNTS.WHATSAPP_BUSINESS_ACCOUNT_ID);
    }
}
