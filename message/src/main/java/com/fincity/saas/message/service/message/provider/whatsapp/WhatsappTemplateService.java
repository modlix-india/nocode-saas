package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappTemplateDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappBusinessAccount;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.TemplateStatus;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappTemplatesRecord;
import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.MessageAccess;
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

    private WhatsappBusinessAccountService businessAccountService;

    @Autowired
    public WhatsappTemplateService(WhatsappApiFactory whatsappApiFactory) {
        this.whatsappApiFactory = whatsappApiFactory;
    }

    @Autowired
    public void setBusinessAccountService(WhatsappBusinessAccountService businessAccountService) {
        this.businessAccountService = businessAccountService;
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
                        access -> this.validateTemplateName(
                                whatsappTemplateRequest.getMessageTemplate().getName()),
                        (access, validationResult) -> super.messageConnectionService.getCoreDocument(
                                access.getAppCode(),
                                access.getClientCode(),
                                whatsappTemplateRequest.getConnectionName()),
                        (access, validationResult, connection) -> this.getBusinessManagementApi(connection),
                        (access, validationResult, connection, api) ->
                                this.getWhatsappBusinessAccount(access, connection),
                        (access, validationResult, connection, api, businessAccount) -> api.createMessageTemplate(
                                businessAccount.getWhatsappBusinessAccountId(),
                                whatsappTemplateRequest.getMessageTemplate()),
                        (access, validationResult, connection, api, businessAccount, apiTemplate) ->
                                super.createInternal(
                                        access,
                                        WhatsappTemplate.of(
                                                businessAccount.getId(),
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
                        access -> this.validateTemplateName(
                                whatsappTemplateRequest.getMessageTemplate().getName()),
                        (access, nameValidationResult) -> super.messageConnectionService.getCoreDocument(
                                access.getAppCode(),
                                access.getClientCode(),
                                whatsappTemplateRequest.getConnectionName()),
                        (access, nameValidationResult, connection) ->
                                super.readIdentityWithAccess(access, whatsappTemplateRequest.getWhatsappTemplateId()),
                        (access, nameValidationResult, connection, existingTemplate) ->
                                this.validateTemplateEditRules(existingTemplate),
                        (access, nameValidationResult, connection, existingTemplate, validationResult) ->
                                this.getBusinessManagementApi(connection),
                        (access, nameValidationResult, connection, existingTemplate, validationResult, api) ->
                                this.getWhatsappBusinessAccount(access, connection),
                        (access,
                                nameValidationResult,
                                connection,
                                existingTemplate,
                                validationResult,
                                api,
                                businessAccount) -> api.updateMessageTemplate(
                                businessAccount.getWhatsappBusinessAccountId(),
                                existingTemplate.getTemplateId(),
                                whatsappTemplateRequest.getMessageTemplate()),
                        (access,
                                nameValidationResult,
                                connection,
                                existingTemplate,
                                validationResult,
                                api,
                                businessAccount,
                                apiTemplate) -> super.update(
                                existingTemplate.update(whatsappTemplateRequest.getMessageTemplate(), apiTemplate)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappTemplateService.updateTemplate"));
    }

    public Mono<WhatsappTemplate> updateTemplateStatus(WhatsappTemplateRequest whatsappTemplateRequest) {

        if (whatsappTemplateRequest.isConnectionNull())
            return this.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        return FlatMapUtil.flatMapMono(
                        super::hasAccess,
                        access -> super.messageConnectionService.getCoreDocument(
                                access.getAppCode(),
                                access.getClientCode(),
                                whatsappTemplateRequest.getConnectionName()),
                        (access, connection) ->
                                super.readIdentityWithAccess(access, whatsappTemplateRequest.getWhatsappTemplateId()),
                        (access, connection, existingTemplate) -> this.getBusinessManagementApi(connection),
                        (access, connection, existingTemplate, api) ->
                                this.getWhatsappBusinessAccount(access, connection),
                        (access, connection, existingTemplate, api, businessAccount) -> api.retrieveTemplates(
                                businessAccount.getWhatsappBusinessAccountId(), existingTemplate.getTemplateName()),
                        (access, connection, existingTemplate, api, businessAccount, retrievedTemplates) -> {
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

    private Mono<WhatsappBusinessAccount> getWhatsappBusinessAccount(MessageAccess access, Connection connection) {
        String businessAccountId = (String) connection
                .getConnectionDetails()
                .getOrDefault(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null)
            return super.throwMissingParam(WhatsappPhoneNumber.Fields.whatsappBusinessAccountId);

        return this.businessAccountService.getBusinessAccount(access, businessAccountId);
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
