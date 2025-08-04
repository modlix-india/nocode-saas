package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_TEMPLATES;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappTemplatesRecord;
import org.springframework.stereotype.Component;

@Component
public class WhatsappTemplateDAO extends BaseProviderDAO<MessageWhatsappTemplatesRecord, WhatsappTemplate> {

    protected WhatsappTemplateDAO() {
        super(
                WhatsappTemplate.class,
                MESSAGE_WHATSAPP_TEMPLATES,
                MESSAGE_WHATSAPP_TEMPLATES.ID,
                MESSAGE_WHATSAPP_TEMPLATES.TEMPLATE_ID);
    }
}
