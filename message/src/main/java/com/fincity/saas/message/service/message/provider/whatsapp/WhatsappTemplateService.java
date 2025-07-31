package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappTemplateDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappTemplatesRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.messages.Language;
import com.fincity.saas.message.model.message.whatsapp.messages.Message.MessageBuilder;
import com.fincity.saas.message.model.message.whatsapp.messages.TemplateMessage;
import com.fincity.saas.message.model.message.whatsapp.templates.type.LanguageType;
import com.fincity.saas.message.model.request.message.MessageRequest;
import com.fincity.saas.message.model.request.message.WhatsappTemplateRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.provider.AbstractMessageProviderService;
import com.fincity.saas.message.service.message.provider.whatsapp.cloud.WhatsappBusinessCloudApi;
import com.fincity.saas.message.model.message.whatsapp.messages.response.MessageResponse;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;
import com.fincity.saas.message.model.message.whatsapp.templates.MessageTemplate;
import com.fincity.saas.message.model.message.whatsapp.templates.response.Template;
import com.fincity.saas.message.model.message.whatsapp.templates.response.MessageTemplates;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import java.util.Map;
import org.jooq.types.ULong;

@Service
public class WhatsappTemplateService
        extends AbstractMessageProviderService<MessageWhatsappTemplatesRecord, WhatsappTemplate, WhatsappTemplateDAO> {

    public static final String WHATSAPP_TEMPLATE_PROVIDER_URI = "/whatsapp/template";

    private static final String WHATSAPP_TEMPLATE_CACHE = "whatsappTemplate";

    private final WhatsappApiFactory whatsappApiFactory;

    @Autowired
    public WhatsappTemplateService(WhatsappApiFactory whatsappApiFactory) {
        this.whatsappApiFactory = whatsappApiFactory;
    }

    @Override
    protected String getCacheName() {
        return WHATSAPP_TEMPLATE_CACHE;
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.WHATSAPP;
    }

    @Override
    public String getProviderUri() {
        return WHATSAPP_TEMPLATE_PROVIDER_URI;
    }

    @Override
    public Mono<Message> toMessage(WhatsappTemplate providerObject) {
        return Mono.just(new Message()
                        .setUserId(providerObject.getUserId())
                        .setMessageProvider(this.getConnectionSubType().getProvider())
                        .setIsOutbound(true) // Templates are typically outbound
                        .setWhatsappTemplateId(providerObject.getId() != null ? providerObject.getId() : null)
                        .setMetadata(providerObject.toMap()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.toMessage"));
    }

    @Override
    public Mono<Message> sendMessage(MessageAccess access, MessageRequest messageRequest, Connection connection) {
        // For basic MessageRequest, we don't have template information
        return this.msgService.<Message>throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST,
                        "Template message sending requires WhatsappTemplateRequest. Use sendTemplateMessage method instead."),
                "unsupported_operation")
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.sendMessage"));
    }

    /**
     * Creates a new WhatsApp template using the WhatsApp Business Management API
     * and saves it to the database.
     *
     * @param access the message access context
     * @param whatsappTemplate the template to create
     * @param connection the WhatsApp connection
     * @return Mono containing the created WhatsappTemplate
     */
    public Mono<WhatsappTemplate> createTemplate(MessageAccess access, WhatsappTemplate whatsappTemplate, Connection connection) {
        return this.validateConnection(connection)
                .then(this.getBusinessManagementApi(connection))
                .flatMap(api -> {
                    // Convert WhatsappTemplate to MessageTemplate for API call
                    MessageTemplate messageTemplate = this.convertToMessageTemplate(whatsappTemplate);
                    
                    return api.createMessageTemplate(
                            whatsappTemplate.getWhatsappBusinessAccountId(),
                            messageTemplate
                    );
                })
                .flatMap(template -> {
                    // Update the WhatsappTemplate with response data
                    whatsappTemplate.setTemplateId(template.getId())
                            .setStatus(template.getStatus())
                            .setAppCode(access.getAppCode())
                            .setClientCode(access.getClientCode());
                    
                    // Save to database
                    return this.createInternal(access, whatsappTemplate);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.createTemplate"));
    }

    /**
     * Updates an existing WhatsApp template using the WhatsApp Business Management API
     * and updates it in the database.
     *
     * @param access the message access context
     * @param whatsappTemplate the template to update
     * @param connection the WhatsApp connection
     * @return Mono containing the updated WhatsappTemplate
     */
    public Mono<WhatsappTemplate> updateTemplate(MessageAccess access, WhatsappTemplate whatsappTemplate, Connection connection) {
        return this.validateConnection(connection)
                .then(this.getBusinessManagementApi(connection))
                .flatMap(api -> {
                    if (whatsappTemplate.getTemplateId() == null) {
                        return this.msgService.<WhatsappTemplate>throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                "missing_template_id");
                    }
                    
                    // Convert WhatsappTemplate to MessageTemplate for API call
                    MessageTemplate messageTemplate = this.convertToMessageTemplate(whatsappTemplate);
                    
                    return api.updateMessageTemplate(
                            whatsappTemplate.getWhatsappBusinessAccountId(),
                            whatsappTemplate.getTemplateId(),
                            messageTemplate
                    );
                })
                .flatMap(apiTemplate -> {
                    // Update the WhatsappTemplate with response data
                    whatsappTemplate.setStatus(apiTemplate.getStatus());
                    
                    // Update in database
                    return this.update(whatsappTemplate);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.updateTemplate"));
    }

    /**
     * Deletes a WhatsApp template using the WhatsApp Business Management API
     * and marks it as inactive in the database.
     *
     * @param access the message access context
     * @param templateName the name of the template to delete
     * @param whatsappBusinessAccountId the WhatsApp business account ID
     * @param connection the WhatsApp connection
     * @return Mono containing the response
     */
    public Mono<Response> deleteTemplate(MessageAccess access, String templateName, String whatsappBusinessAccountId, Connection connection) {
        return this.validateConnection(connection)
                .then(this.getBusinessManagementApi(connection))
                .flatMap(api -> api.deleteMessageTemplate(whatsappBusinessAccountId, templateName))
                .flatMap(response -> {
                    // Find and soft delete the template in database
                    return this.dao.findByTemplateNameAndAccount(templateName, whatsappBusinessAccountId)
                            .flatMap(template -> {
                                template.setActive(false);
                                return this.update(template)
                                        .thenReturn(response);
                            })
                            .switchIfEmpty(Mono.just(response));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.deleteTemplate"));
    }

    /**
     * Retrieves all templates for a WhatsApp business account.
     *
     * @param whatsappBusinessAccountId the WhatsApp business account ID
     * @param connection the WhatsApp connection
     * @return Mono containing MessageTemplates response
     */
    public Mono<MessageTemplates> getTemplates(String whatsappBusinessAccountId, Connection connection) {
        return this.validateConnection(connection)
                .then(this.getBusinessManagementApi(connection))
                .flatMap(api -> api.retrieveTemplates(whatsappBusinessAccountId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.getTemplates"));
    }

    /**
     * Retrieves a specific template by name for a WhatsApp business account.
     *
     * @param templateName the name of the template
     * @param whatsappBusinessAccountId the WhatsApp business account ID
     * @param connection the WhatsApp connection
     * @return Mono containing MessageTemplates response
     */
    public Mono<MessageTemplates> getTemplateByName(String templateName, String whatsappBusinessAccountId, Connection connection) {
        return this.validateConnection(connection)
                .then(this.getBusinessManagementApi(connection))
                .flatMap(api -> api.retrieveTemplates(whatsappBusinessAccountId, templateName))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.getTemplateByName"));
    }

    /**
     * Checks the status of a template by retrieving it from WhatsApp API.
     *
     * @param templateName the name of the template
     * @param whatsappBusinessAccountId the WhatsApp business account ID
     * @param connection the WhatsApp connection
     * @return Mono containing the template status
     */
    public Mono<String> checkTemplateStatus(String templateName, String whatsappBusinessAccountId, Connection connection) {
        return this.getTemplateByName(templateName, whatsappBusinessAccountId, connection)
                .map(templates -> {
                    if (templates.getData() != null && !templates.getData().isEmpty()) {
                        return templates.getData().getFirst().getStatus();
                    }
                    return "NOT_FOUND";
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.checkTemplateStatus"));
    }

    /**
     * Synchronizes template status from WhatsApp API to local database.
     *
     * @param access the message access context
     * @param templateName the name of the template
     * @param whatsappBusinessAccountId the WhatsApp business account ID
     * @param connection the WhatsApp connection
     * @return Mono containing the updated WhatsappTemplate
     */
    public Mono<WhatsappTemplate> syncTemplateStatus(MessageAccess access, String templateName, String whatsappBusinessAccountId, Connection connection) {
        return this.getTemplateByName(templateName, whatsappBusinessAccountId, connection)
                .flatMap(templates -> {
                    if (templates.getData() != null && !templates.getData().isEmpty()) {
                        Template apiTemplate = templates.getData().get(0);
                        
                        return this.dao.findByTemplateNameAndAccount(templateName, whatsappBusinessAccountId)
                                .flatMap(localTemplate -> {
                                    localTemplate.setStatus(apiTemplate.getStatus());
                                    return this.update(localTemplate);
                                })
                                .switchIfEmpty(this.msgService.<WhatsappTemplate>throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        "template_not_found_in_database"));
                    }
                    return this.msgService.<WhatsappTemplate>throwMessage(
                            msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                            "template_not_found_in_whatsapp");
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.syncTemplateStatus"));
    }

    /**
     * Validates the WhatsApp connection.
     *
     * @param connection the connection to validate
     * @return Mono<Boolean> indicating validation result
     */
    private Mono<Boolean> validateConnection(Connection connection) {
        return this.isValidConnection(connection);
    }

    /**
     * Creates a WhatsApp Business Management API instance from the connection.
     *
     * @param connection the WhatsApp connection
     * @return Mono containing WhatsappBusinessManagementApi
     */
    private Mono<WhatsappBusinessManagementApi> getBusinessManagementApi(Connection connection) {
        return this.whatsappApiFactory.newBusinessManagementApiFromConnection(connection)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                        "failed_to_create_whatsapp_api"));
    }

    /**
     * Converts WhatsappTemplate to MessageTemplate for API calls.
     *
     * @param whatsappTemplate the WhatsappTemplate to convert
     * @return MessageTemplate for API usage
     */
    private MessageTemplate convertToMessageTemplate(WhatsappTemplate whatsappTemplate) {
        MessageTemplate messageTemplate = new MessageTemplate();
        messageTemplate.setName(whatsappTemplate.getTemplateName());
        messageTemplate.setCategory(whatsappTemplate.getCategory());
        
        if (whatsappTemplate.getComponents() != null) {
            messageTemplate.setComponents(whatsappTemplate.getComponents());
        }
        
        return messageTemplate;
    }
}
