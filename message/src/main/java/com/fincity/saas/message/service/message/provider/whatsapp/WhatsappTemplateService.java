package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappTemplateDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.TemplateStatus;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappTemplatesRecord;
import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.message.whatsapp.templates.response.Template;
import com.fincity.saas.message.model.request.message.provider.whatsapp.business.WhatsappTemplateRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import com.fincity.saas.message.service.message.provider.whatsapp.business.WhatsappBusinessManagementApi;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class WhatsappTemplateService
        extends AbstractMessageService<MessageWhatsappTemplatesRecord, WhatsappTemplate, WhatsappTemplateDAO> {

    public static final String WHATSAPP_TEMPLATE_PROVIDER_URI = "/whatsapp/template";

    private static final String WHATSAPP_TEMPLATE_CACHE = "whatsappTemplate";
    private static final Set<TemplateStatus> EDITABLE_STATUSES =
            Set.of(TemplateStatus.APPROVED, TemplateStatus.REJECTED, TemplateStatus.PAUSED);
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
    public MessageSeries getMessageSeries() {
        return MessageSeries.WHATSAPP_TEMPLATE;
    }

    @Override
    protected Mono<WhatsappTemplate> updatableEntity(WhatsappTemplate entity) {
        return super.updatableEntity(entity).flatMap(uEntity -> {
            uEntity.setCategory(entity.getCategory());
            uEntity.setComponents(entity.getComponents());
            uEntity.setMessageSendTtlSeconds(entity.getMessageSendTtlSeconds());
            uEntity.setParameterFormat(entity.getParameterFormat());

            return Mono.just(uEntity);
        });
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.WHATSAPP;
    }

    @Override
    public String getProviderUri() {
        return WHATSAPP_TEMPLATE_PROVIDER_URI;
    }

    public Mono<WhatsappTemplate> createTemplate(WhatsappTemplateRequest whatsappTemplateRequest) {

        if (whatsappTemplateRequest.isConnectionNull())
            return this.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        if (whatsappTemplateRequest.getMessageTemplate().hadHeaderMediaFile()
                && (whatsappTemplateRequest.getFileDetail() == null
                        || whatsappTemplateRequest.getFileDetail().isEmpty()))
            return super.throwMissingParam(WhatsappTemplateRequest.Fields.fileDetail);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        messageAccess -> this.validateTemplateName(
                                whatsappTemplateRequest.getMessageTemplate().getName()),
                        (messageAccess, validationResult) -> super.messageConnectionService.getCoreDocument(
                                messageAccess.getAppCode(),
                                messageAccess.getClientCode(),
                                whatsappTemplateRequest.getConnectionName()),
                        (messageAccess, validationResult, connection) -> this.getBusinessManagementApi(connection),
                        (messageAccess, validationResult, connection, api) ->
                                this.getWhatsappBusinessAccountId(connection),
                        (messageAccess, validationResult, connection, api, businessAccountId) ->
                                api.createMessageTemplate(
                                        businessAccountId, whatsappTemplateRequest.getMessageTemplate()),
                        (messageAccess, validationResult, connection, api, businessAccountId, apiTemplate) ->
                                super.createInternal(
                                        messageAccess,
                                        WhatsappTemplate.of(
                                                businessAccountId,
                                                whatsappTemplateRequest.getMessageTemplate(),
                                                apiTemplate,
                                                whatsappTemplateRequest.getFileDetail())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.createTemplate"));
    }

    public Mono<WhatsappTemplate> updateTemplate(WhatsappTemplateRequest whatsappTemplateRequest) {

        if (whatsappTemplateRequest.isConnectionNull())
            return this.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        messageAccess -> this.validateTemplateName(
                                whatsappTemplateRequest.getMessageTemplate().getName()),
                        (messageAccess, nameValidationResult) -> super.messageConnectionService.getCoreDocument(
                                messageAccess.getAppCode(),
                                messageAccess.getClientCode(),
                                whatsappTemplateRequest.getConnectionName()),
                        (messageAccess, nameValidationResult, connection) -> super.readIdentityWithAccess(
                                messageAccess, whatsappTemplateRequest.getWhatsappTemplateId()),
                        (messageAccess, nameValidationResult, connection, existingTemplate) ->
                                this.validateTemplateEditRules(existingTemplate),
                        (messageAccess, nameValidationResult, connection, existingTemplate, validationResult) ->
                                this.getBusinessManagementApi(connection),
                        (messageAccess, nameValidationResult, connection, existingTemplate, validationResult, api) ->
                                this.getWhatsappBusinessAccountId(connection),
                        (messageAccess,
                                nameValidationResult,
                                connection,
                                existingTemplate,
                                validationResult,
                                api,
                                businessAccountId) -> api.updateMessageTemplate(
                                businessAccountId,
                                existingTemplate.getTemplateId(),
                                whatsappTemplateRequest.getMessageTemplate()),
                        (messageAccess,
                                nameValidationResult,
                                connection,
                                existingTemplate,
                                validationResult,
                                api,
                                businessAccountId,
                                apiTemplate) -> super.update(
                                existingTemplate.update(whatsappTemplateRequest.getMessageTemplate(), apiTemplate)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.updateTemplate"));
    }

    public Mono<WhatsappTemplate> updateTemplateStatus(WhatsappTemplateRequest whatsappTemplateRequest) {

        if (whatsappTemplateRequest.isConnectionNull())
            return this.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        messageAccess -> super.messageConnectionService.getCoreDocument(
                                messageAccess.getAppCode(),
                                messageAccess.getClientCode(),
                                whatsappTemplateRequest.getConnectionName()),
                        (messageAccess, connection) -> super.readIdentityWithAccess(
                                messageAccess, whatsappTemplateRequest.getWhatsappTemplateId()),
                        (messageAccess, connection, existingTemplate) -> this.getBusinessManagementApi(connection),
                        (messageAccess, connection, existingTemplate, api) ->
                                this.getWhatsappBusinessAccountId(connection),
                        (messageAccess, connection, existingTemplate, api, businessAccountId) ->
                                api.retrieveTemplates(businessAccountId, existingTemplate.getTemplateName()),
                        (messageAccess, connection, existingTemplate, api, businessAccountId, retrievedTemplates) -> {
                            if (retrievedTemplates.getData() == null
                                    || retrievedTemplates.getData().isEmpty())
                                return super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        MessageResourceService.TEMPLATE_NOT_FOUND_IN_WHATSAPP,
                                        existingTemplate.getTemplateId());

                            Template apiTemplate = retrievedTemplates.getData().getFirst();

                            if (!apiTemplate.getId().equals(existingTemplate.getTemplateId()))
                                return super.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        MessageResourceService.TEMPLATE_NOT_FOUND_IN_WHATSAPP,
                                        existingTemplate.getTemplateId());

                            existingTemplate.setStatus(apiTemplate.getStatus());

                            if (apiTemplate.getStatus().equals(TemplateStatus.REJECTED))
                                existingTemplate.setRejectedReason(apiTemplate.getRejectedReason());

                            return super.update(existingTemplate);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.updateTemplateStatus"));
    }

    private Mono<String> getWhatsappBusinessAccountId(Connection connection) {
        String businessAccountId = (String)
                connection.getConnectionDetails().getOrDefault(WhatsappTemplate.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return super.throwMissingParam(WhatsappTemplate.Fields.whatsappBusinessAccountId);

        return Mono.just(businessAccountId);
    }

    private Mono<Boolean> validateTemplateEditRules(WhatsappTemplate existingTemplate) {

        if (!EDITABLE_STATUSES.contains(existingTemplate.getStatus()))
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.TEMPLATE_NOT_EDITABLE_STATUS,
                    existingTemplate.getStatus().toString());

        LocalDateTime lastUpdate = existingTemplate.getUpdatedAt();
        if (lastUpdate != null) {
            LocalDate today = LocalDate.now();
            LocalDate lastUpdateDate = lastUpdate.toLocalDate();

            if (lastUpdateDate.equals(today))
                return super.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.TEMPLATE_DAILY_EDIT_LIMIT_EXCEEDED);
        }

        // TODO: Add Scheduler to reset monthly edit counts and implement monthly edit count logic

        return Mono.just(Boolean.TRUE);
    }

    private Mono<Boolean> validateTemplateName(String templateName) {
        if (templateName == null || templateName.isEmpty() || templateName.length() > 512)
            return super.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    MessageResourceService.TEMPLATE_NAME_LENGTH_EXCEEDED);
        return Mono.just(Boolean.TRUE);
    }

    private Mono<WhatsappBusinessManagementApi> getBusinessManagementApi(Connection connection) {
        return this.whatsappApiFactory
                .newBusinessManagementApiFromConnection(connection)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                        "failed_to_create_whatsapp_api"));
    }
}
