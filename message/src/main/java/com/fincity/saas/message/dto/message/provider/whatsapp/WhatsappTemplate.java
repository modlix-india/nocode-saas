package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.Category;
import com.fincity.saas.message.model.message.whatsapp.templates.ComponentList;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WhatsappTemplate extends BaseUpdatableDto<WhatsappTemplate> {

    @Serial
    private static final long serialVersionUID = 1234567890123456789L;

    private String templateId;
    private String templateName;
    private String language;
    private Category category;
    private String status;
    private Category previousCategory;
    private ComponentList components;
    private String whatsappBusinessAccountId;

    public WhatsappTemplate() {
        super();
    }

    public WhatsappTemplate(String templateName, String language, Category category, String whatsappBusinessAccountId) {
        super();
        this.templateName = templateName;
        this.language = language;
        this.category = category;
        this.whatsappBusinessAccountId = whatsappBusinessAccountId;
    }

    public WhatsappTemplate(WhatsappTemplate whatsappTemplate) {
        super(whatsappTemplate);
        this.templateId = whatsappTemplate.getTemplateId();
        this.templateName = whatsappTemplate.getTemplateName();
        this.language = whatsappTemplate.getLanguage();
        this.category = whatsappTemplate.getCategory();
        this.status = whatsappTemplate.getStatus();
        this.previousCategory = whatsappTemplate.getPreviousCategory();
        this.components = whatsappTemplate.getComponents();
        this.whatsappBusinessAccountId = whatsappTemplate.getWhatsappBusinessAccountId();
    }
}
