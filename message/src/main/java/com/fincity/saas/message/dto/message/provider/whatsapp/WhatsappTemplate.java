package com.fincity.saas.message.dto.message.provider.whatsapp;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.Category;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.SubCategory;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.TemplateRejectedReason;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.TemplateStatus;
import com.fincity.saas.message.model.message.whatsapp.templates.ComponentList;
import com.fincity.saas.message.model.message.whatsapp.templates.MessageTemplate;
import com.fincity.saas.message.model.message.whatsapp.templates.response.Template;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.ParameterFormat;

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

    private String whatsappBusinessAccountId;
    private String templateId;
    private String templateName;
    private Boolean allowCategoryChange;
    private Category category;
    private SubCategory subCategory;
    private ULong messageSendTtlSeconds;
    private ParameterFormat parameterFormat = ParameterFormat.POSITIONAL;
    private String language;
    private TemplateStatus status;
    private TemplateRejectedReason rejectedReason;
    private Category previousCategory;
    private ComponentList components;
    private Integer monthlyEditCount = 0;

    public static WhatsappTemplate of(
            String whatsappBusinessAccountId, MessageTemplate messageTemplate, Template template) {
        WhatsappTemplate whatsappTemplate =
                new WhatsappTemplate().setWhatsappBusinessAccountId(whatsappBusinessAccountId);

        if (template != null) {
            whatsappTemplate
                    .setTemplateId(template.getId())
                    .setTemplateName(template.getName())
                    .setLanguage(template.getLanguage())
                    .setStatus(template.getStatus())
                    .setPreviousCategory(template.getPreviousCategory());

            if (template.getComponents() != null) {
                ComponentList componentList = new ComponentList();
                componentList.addAll(template.getComponents());
                whatsappTemplate.setComponents(componentList);
            }

            if (template.getRejectedReason() != null) whatsappTemplate.setRejectedReason(template.getRejectedReason());
        }

        if (messageTemplate != null)
            whatsappTemplate
                    .setAllowCategoryChange(messageTemplate.isAllowCategoryChange())
                    .setCategory(messageTemplate.getCategory())
                    .setSubCategory(messageTemplate.getSubCategory())
                    .setMessageSendTtlSeconds(ULongUtil.valueOf(messageTemplate.getMessageSendTtlSeconds()))
                    .setParameterFormat(messageTemplate.getParameterFormat());

        return whatsappTemplate;
    }

    public WhatsappTemplate update(MessageTemplate messageTemplate, Template template) {

        if (template != null) {
            this.setTemplateId(template.getId())
                    .setStatus(template.getStatus())
                    .setPreviousCategory(template.getPreviousCategory());

            if (template.getComponents() != null) {
                ComponentList componentList = new ComponentList();
                componentList.addAll(template.getComponents());
                this.setComponents(componentList);
            }

            if (template.getRejectedReason() != null) this.setRejectedReason(template.getRejectedReason());
        }

        if (messageTemplate != null)
            this.setCategory(messageTemplate.getCategory())
                    .setMessageSendTtlSeconds(ULongUtil.valueOf(messageTemplate.getMessageSendTtlSeconds()))
                    .setParameterFormat(messageTemplate.getParameterFormat());

        return this;
    }
}
