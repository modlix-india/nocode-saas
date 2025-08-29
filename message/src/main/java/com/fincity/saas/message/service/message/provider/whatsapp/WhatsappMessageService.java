package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappMessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageType;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappMessagesRecord;
import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.messages.Message.MessageBuilder;
import com.fincity.saas.message.model.message.whatsapp.messages.TextMessage;
import com.fincity.saas.message.model.message.whatsapp.webhook.IChange;
import com.fincity.saas.message.model.message.whatsapp.webhook.IEntry;
import com.fincity.saas.message.model.message.whatsapp.webhook.IMessage;
import com.fincity.saas.message.model.message.whatsapp.webhook.IMetadata;
import com.fincity.saas.message.model.message.whatsapp.webhook.IStatus;
import com.fincity.saas.message.model.message.whatsapp.webhook.IValue;
import com.fincity.saas.message.model.message.whatsapp.webhook.IWebHookEvent;
import com.fincity.saas.message.model.request.message.MessageRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMessageRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.provider.AbstractMessageService;
import com.fincity.saas.message.service.message.provider.whatsapp.api.WhatsappApiFactory;
import com.fincity.saas.message.util.PhoneUtil;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class WhatsappMessageService
        extends AbstractMessageService<MessageWhatsappMessagesRecord, WhatsappMessage, WhatsappMessageDAO> {

    public static final String WHATSAPP_PROVIDER_URI = "/whatsapp";
    private static final String WHATSAPP_MESSAGE_CACHE = "whatsappMessage";
    private static final String SUBSCRIBE = "subscribe";

    private final WhatsappApiFactory whatsappApiFactory;

    private final WhatsappPhoneNumberService whatsappPhoneNumberService;

    private final WhatsappCswService customerServiceWindowService;

    @Value("${meta.webhook.verify-token:null}")
    private String verifyToken;

    @Autowired
    public WhatsappMessageService(
            WhatsappApiFactory whatsappApiFactory,
            WhatsappPhoneNumberService whatsappPhoneNumberService,
            WhatsappCswService customerServiceWindowService) {
        this.whatsappApiFactory = whatsappApiFactory;
        this.whatsappPhoneNumberService = whatsappPhoneNumberService;
        this.customerServiceWindowService = customerServiceWindowService;
    }

    @Override
    protected String getCacheName() {
        return WHATSAPP_MESSAGE_CACHE;
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.WHATSAPP_MESSAGE;
    }

    @Override
    public ConnectionSubType getConnectionSubType() {
        return ConnectionSubType.WHATSAPP;
    }

    public String getProviderUri() {
        return WHATSAPP_PROVIDER_URI;
    }

    @Override
    public Mono<Message> toMessage(WhatsappMessage providerObject) {
        return Mono.just(new Message()
                        .setUserId(providerObject.getUserId())
                        .setFromDialCode(providerObject.getFromDialCode())
                        .setFrom(providerObject.getFrom())
                        .setToDialCode(providerObject.getToDialCode())
                        .setTo(providerObject.getTo())
                        .setMessageProvider(this.getConnectionSubType().getProvider())
                        .setIsOutbound(providerObject.isOutbound())
                        .setMessageStatus(providerObject.getMessageStatus())
                        .setSentTime(
                                providerObject.getSentTime() != null
                                        ? providerObject.getSentTime().toString()
                                        : null)
                        .setDeliveredTime(
                                providerObject.getDeliveredTime() != null
                                        ? providerObject.getDeliveredTime().toString()
                                        : null)
                        .setReadTime(
                                providerObject.getReadTime() != null
                                        ? providerObject.getReadTime().toString()
                                        : null)
                        .setWhatsappMessageId(providerObject.getId() != null ? providerObject.getId() : null)
                        .setMetadata(providerObject.toMap()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.toMessage"));
    }

    @Override
    public Mono<Message> sendMessage(MessageAccess access, MessageRequest messageRequest, Connection connection) {

        if (!messageRequest.isValid()) return super.throwMissingParam(MessageRequest.Fields.text);

        if (messageRequest.isConnectionNull()) return super.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        WhatsappMessage whatsappMessage = WhatsappMessage.ofOutbound(
                MessageBuilder.builder()
                        .setTo(messageRequest.getToNumber().getNumber())
                        .buildTextMessage(new TextMessage().setBody(messageRequest.getText())),
                PhoneUtil.parse(access.getUser().getPhoneNumber()));

        return this.sendMessageInternal(access, connection, null, whatsappMessage);
    }

    public Mono<Message> sendMessage(WhatsappMessageRequest whatsappMessageRequest) {

        if (!whatsappMessageRequest.isValid()) return super.throwMissingParam("message");

        if (whatsappMessageRequest.isConnectionNull())
            return super.throwMissingParam(BaseMessageRequest.Fields.connectionName);

        return FlatMapUtil.flatMapMono(
                super::hasAccess,
                access -> this.messageConnectionService.getCoreDocument(
                        access.getAppCode(), access.getClientCode(), whatsappMessageRequest.getConnectionName()),
                (access, connection) -> this.sendMessageInternal(
                        access,
                        connection,
                        whatsappMessageRequest.getWhatsappPhoneNumberId(),
                        WhatsappMessage.ofOutbound(
                                whatsappMessageRequest.getMessage(),
                                PhoneUtil.parse(access.getUser().getPhoneNumber()))));
    }

    private Mono<Message> sendMessageInternal(
            MessageAccess access,
            Connection connection,
            Identity whatsappPhoneNumberId,
            WhatsappMessage whatsappMessage) {

        return FlatMapUtil.flatMapMono(
                        () -> super.isValidConnection(connection),
                        vConn -> this.getWhatsappBusinessAccountId(connection),
                        (vConn, businessAccountId) ->
                                this.getWhatsappPhoneNumber(whatsappPhoneNumberId, access, businessAccountId),
                        (vConn, businessAccountId, phoneNumberId) ->
                                this.validateCustomerServiceWindow(access, phoneNumberId, whatsappMessage),
                        (vConn, businessAccountId, phoneNumberId, validated) ->
                                this.whatsappApiFactory.newBusinessCloudApiFromConnection(connection),
                        (vConn, businessAccountId, phoneNumberId, validated, api) ->
                                api.sendMessage(phoneNumberId.getPhoneNumberId(), whatsappMessage.getMessage()),
                        (vConn, businessAccountId, phoneNumberId, validated, api, response) -> {
                            whatsappMessage.setWhatsappBusinessAccountId(businessAccountId);
                            whatsappMessage.setWhatsappPhoneNumberId(phoneNumberId.getId());
                            whatsappMessage.setCustomerWaId(
                                    response.getContacts().getFirst().getWaId());
                            whatsappMessage.setMessageId(
                                    response.getMessages().getFirst().getId());
                            whatsappMessage.setMessageResponse(response);
                            return this.createInternal(access, whatsappMessage);
                        },
                        (vConn, businessAccountId, phoneNumberId, validated, api, response, created) ->
                                this.toMessage(created).map(msg -> msg.setConnectionName(connection.getName())),
                        (vConn, businessAccountId, phoneNumberId, validated, api, response, created, msg) ->
                                this.messageEventService
                                        .sendMessageEvent(
                                                access.getAppCode(),
                                                access.getClientCode(),
                                                access.getUserId(),
                                                created)
                                        .thenReturn(msg))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.sendMessageInternal"));
    }

    public Mono<String> verifyMetaWebhook(String mode, String token, String challenge) {

        logger.info("Received webhook verification request: mode={}, token={}, challenge={}", mode, token, challenge);

        return SUBSCRIBE.equals(mode) && verifyToken.equals(token) ? Mono.just(challenge) : Mono.empty();
    }

    private Mono<String> getWhatsappBusinessAccountId(Connection connection) {
        String businessAccountId = (String)
                connection.getConnectionDetails().getOrDefault(WhatsappMessage.Fields.whatsappBusinessAccountId, null);

        if (businessAccountId == null) return super.throwMissingParam(WhatsappMessage.Fields.whatsappBusinessAccountId);

        return Mono.just(businessAccountId);
    }

    private Mono<WhatsappPhoneNumber> getWhatsappPhoneNumber(
            Identity whatsappPhoneNumberId, MessageAccess access, String businessAccountId) {
        if (whatsappPhoneNumberId != null && !whatsappPhoneNumberId.isNull())
            return whatsappPhoneNumberService
                    .readIdentityWithAccessEmpty(access, whatsappPhoneNumberId)
                    .switchIfEmpty(this.getAccountWhatsappPhoneNumber(access, businessAccountId));

        return this.getAccountWhatsappPhoneNumber(access, businessAccountId);
    }

    private Mono<WhatsappPhoneNumber> getAccountWhatsappPhoneNumber(MessageAccess access, String businessAccountId) {
        return whatsappPhoneNumberService
                .getByAccountId(access, businessAccountId)
                .switchIfEmpty(super.throwMissingParam(WhatsappMessage.Fields.whatsappPhoneNumberId));
    }

    public Mono<Void> processWebhookEvent(String appCode, String clientCode, IWebHookEvent event) {
        if (event == null || event.getEntry() == null) return Mono.empty();

        return Flux.fromIterable(event.getEntry())
                .flatMap(entry -> processEntry(appCode, clientCode, entry))
                .then()
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.processWebhookEvent"));
    }

    private Mono<Void> processEntry(String appCode, String clientCode, IEntry entry) {
        if (entry.getChanges() == null) return Mono.empty();

        return Flux.fromIterable(entry.getChanges())
                .flatMap(change -> processChange(appCode, clientCode, change))
                .then();
    }

    private Mono<Void> processChange(String appCode, String clientCode, IChange change) {
        if (change.getValue() == null) return Mono.empty();

        IValue value = change.getValue();

        if (value.getMessages() != null && !value.getMessages().isEmpty()) {
            return Flux.fromIterable(value.getMessages())
                    .flatMap(message -> processIncomingMessage(appCode, clientCode, message, value.getMetadata()))
                    .then();
        } else if (value.getStatuses() != null && !value.getStatuses().isEmpty()) {
            return this.processStatusUpdates(value.getStatuses());
        }

        return Mono.empty();
    }

    private Mono<WhatsappMessage> processIncomingMessage(
            String appCode, String clientCode, IMessage message, IMetadata metadata) {

        logger.info("Processing incoming message: {} from {}", message.getId(), message.getFrom());

        String phoneNumberId = metadata != null ? metadata.getPhoneNumberId() : null;
        if (phoneNumberId == null) {
            logger.error("Phone number ID is null for incoming message: {}", message.getId());
            return Mono.empty();
        }

        MessageAccess access = MessageAccess.of(appCode, clientCode, true);

        return this.whatsappPhoneNumberService
                .getByPhoneNumberId(access, phoneNumberId)
                .flatMap(whatsappPhoneNumber -> this.createInternal(
                        MessageAccess.of(appCode, clientCode, whatsappPhoneNumber.getUserId(), Boolean.TRUE),
                        WhatsappMessage.ofInbound(message, appCode, whatsappPhoneNumber.getId())));
    }

    private Mono<Void> processStatusUpdates(List<IStatus> statuses) {
        logger.info("Processing {} status updates", statuses.size());
        return Flux.fromIterable(statuses).flatMap(this::processStatusUpdate).then();
    }

    private Mono<Void> processStatusUpdate(IStatus status) {
        logger.info("Processing status update for message: {} - status: {}", status.getId(), status.getStatus());

        return this.dao
                .findByUniqueField(status.getId())
                .flatMap(whatsappMessage -> updateMessageStatus(whatsappMessage, status))
                .doOnSuccess(updated -> {
                    if (updated != null) {
                        logger.info("Updated message status: {} -> {}", status.getId(), status.getStatus());
                    }
                })
                .then()
                .onErrorResume(error -> {
                    logger.error("Failed to update message status for {}: {}", status.getId(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<WhatsappMessage> updateMessageStatus(WhatsappMessage whatsappMessage, IStatus status) {
        whatsappMessage.setMessageStatus(status.getStatus());

        if (status.getTimestamp() != null) {
            long timestamp = Long.parseLong(status.getTimestamp());
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);

            switch (status.getStatus()) {
                case DELIVERED:
                    whatsappMessage.setDeliveredTime(dateTime);
                    break;
                case READ:
                    whatsappMessage.setReadTime(dateTime);
                    break;
                case FAILED:
                    whatsappMessage.setFailedTime(dateTime);
                    if (status.getErrors() != null && !status.getErrors().isEmpty())
                        whatsappMessage.setFailureReason(
                                status.getErrors().getFirst().getTitle());
                    break;
                default:
                    break;
            }
        }

        return super.update(whatsappMessage);
    }

    private Mono<Boolean> validateCustomerServiceWindow(
            MessageAccess access, WhatsappPhoneNumber whatsappPhoneNumber, WhatsappMessage whatsappMessage) {

        PhoneNumber customerPhone = PhoneNumber.of(whatsappMessage.getToDialCode(), whatsappMessage.getTo());

        boolean isTemplateMessage = whatsappMessage.getMessageType() == MessageType.TEMPLATE;

        return customerServiceWindowService
                .canSendMessage(access, whatsappPhoneNumber, customerPhone, isTemplateMessage)
                .flatMap(canSend -> {
                    if (Boolean.FALSE.equals(canSend)) {
                        return Mono.error(
                                new GenericException(
                                        HttpStatus.BAD_REQUEST,
                                        "Cannot send non-template message outside customer service window. "
                                                + "Customer service window is open for 24 hours after receiving a message from the customer. "
                                                + "Use template messages to initiate conversations or send messages outside the window."));
                    }
                    return Mono.just(true);
                });
    }

    public Mono<WhatsappCswService.CswStatus> getCustomerServiceWindowStatus(
            MessageAccess access, Identity whatsappPhoneNumberId, String customerPhoneNumber) {

        PhoneNumber customerPhone = PhoneNumber.of(customerPhoneNumber);

        return FlatMapUtil.flatMapMono(
                () -> this.whatsappPhoneNumberService.readIdentityWithAccessEmpty(access, whatsappPhoneNumberId),
                whatsappPhoneNumber -> customerServiceWindowService.getCustomerServiceWindowStatus(
                        access, whatsappPhoneNumber, customerPhone));
    }

    public Mono<Boolean> canSendNonTemplateMessage(
            MessageAccess access, Identity whatsappPhoneNumberId, String customerPhoneNumber) {

        return getCustomerServiceWindowStatus(access, whatsappPhoneNumberId, customerPhoneNumber)
                .map(WhatsappCswService.CswStatus::canSendNonTemplateMessage);
    }
}
