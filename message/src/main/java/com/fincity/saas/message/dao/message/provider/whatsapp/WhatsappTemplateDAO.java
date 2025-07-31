package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.Tables.MESSAGE_WHATSAPP_TEMPLATES;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappTemplatesRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class WhatsappTemplateDAO extends BaseProviderDAO<MessageWhatsappTemplatesRecord, WhatsappTemplate> {

    protected WhatsappTemplateDAO() {
        super(
                WhatsappTemplate.class,
                MESSAGE_WHATSAPP_TEMPLATES,
                MESSAGE_WHATSAPP_TEMPLATES.ID,
                MESSAGE_WHATSAPP_TEMPLATES.TEMPLATE_ID);
    }

    /**
     * Finds a WhatsappTemplate by template name and WhatsApp business account ID.
     * 
     * @param templateName the template name
     * @param whatsappBusinessAccountId the WhatsApp business account ID
     * @return Mono containing the WhatsappTemplate if found
     */
    public Mono<WhatsappTemplate> findByTemplateNameAndAccount(String templateName, String whatsappBusinessAccountId) {
        return Mono.from(dslContext.selectFrom(MESSAGE_WHATSAPP_TEMPLATES)
                .where(MESSAGE_WHATSAPP_TEMPLATES.TEMPLATE_NAME.eq(templateName)
                        .and(MESSAGE_WHATSAPP_TEMPLATES.WHATSAPP_BUSINESS_ACCOUNT_ID.eq(whatsappBusinessAccountId))
                        .and(MESSAGE_WHATSAPP_TEMPLATES.IS_ACTIVE.eq(true))))
                .map(record -> record.into(pojoClass));
    }
}
