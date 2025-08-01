package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappMessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageStatus;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappMessagesRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.messages.Message.MessageBuilder;
import com.fincity.saas.message.model.message.whatsapp.messages.TextMessage;
import com.fincity.saas.message.model.message.whatsapp.messages.type.MessageType;
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
import com.fincity.saas.message.service.message.provider.AbstractMessageProviderService;
import com.fincity.saas.message.util.PhoneUtil;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class WhatsappMessageService
        extends AbstractMessageProviderService<MessageWhatsappMessagesRecord, WhatsappMessage, WhatsappMessageDAO> {

    public static final String WHATSAPP_PROVIDER_URI = "/whatsapp";

    private static final String WHATSAPP_MESSAGE_CACHE = "whatsappMessage";

    private final WhatsappApiFactory whatsappApiFactory;

    @Value("${facebook.whatsapp.webhook.verify-token}")
    private String verifyToken;

    @Autowired
    public WhatsappMessageService(WhatsappApiFactory whatsappApiFactory) {
        this.whatsappApiFactory = whatsappApiFactory;
    }

    @Override
    protected String getCacheName() {
        return WHATSAPP_MESSAGE_CACHE;
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
                        .setMessageStatus(providerObject.getStatus())
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

        if (!messageRequest.isValid()) return super.throwMissingParam("text");

        var message = MessageBuilder.builder()
                .setTo(messageRequest.getToNumber().getNumber())
                .buildTextMessage(new TextMessage().setBody(messageRequest.getText()));

        WhatsappMessage whatsappMessage =
                this.createWhatsappMessage(access, messageRequest.getToNumber().getNumber(), MessageType.TEXT);

        return this.sendMessageInternal(
                access, connection, message, whatsappMessage, "WhatsappMessageService.sendTextMessage");
    }

    public Mono<Message> sendWhatsappMessage(
            MessageAccess access, WhatsappMessageRequest whatsappMessageRequest, Connection connection) {

        if (!whatsappMessageRequest.isValid()) return super.throwMissingParam("message");

        var message = whatsappMessageRequest.getMessage();

        WhatsappMessage whatsappMessage = this.createWhatsappMessage(access, message.getTo(), message.getType());

        return this.sendMessageInternal(
                access, connection, message, whatsappMessage, "WhatsappMessageService.sendWhatsappMessage");
    }

    private Mono<Message> sendMessageInternal(
            MessageAccess access,
            Connection connection,
            com.fincity.saas.message.model.message.whatsapp.messages.Message message,
            WhatsappMessage whatsappMessage,
            String methodName) {

        String phoneNumberId =
                (String) connection.getConnectionDetails().getOrDefault(WhatsappMessage.Fields.phoneNumberId, null);
        if (phoneNumberId == null) return super.throwMissingParam(WhatsappMessage.Fields.phoneNumberId);

        whatsappMessage.setPhoneNumberId(phoneNumberId);

        return FlatMapUtil.flatMapMono(
                        () -> super.isValidConnection(connection),
                        vConn -> this.whatsappApiFactory.newBusinessCloudApiFromConnection(connection),
                        (vConn, api) -> api.sendMessage(phoneNumberId, message),
                        (vConn, api, response) -> {
                            whatsappMessage.setMessageId(
                                    response.getMessages().getFirst().getId());
                            whatsappMessage.setOutMessage(message);
                            whatsappMessage.setMessageResponse(response);
                            return this.createInternal(access, whatsappMessage);
                        },
                        (vConn, api, response, created) ->
                                this.toMessage(created).map(msg -> msg.setConnectionName(connection.getName())),
                        (vConn, api, response, created, msg) -> this.messageEventService
                                .sendMessageEvent(
                                        access.getAppCode(), access.getClientCode(), access.getUserId(), created)
                                .thenReturn(msg))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, methodName));
    }

    public Mono<String> verifyWebhook(String mode, String token, String challenge) {

        logger.info("Received webhook verification request: mode={}, token={}, challenge={}", mode, token, challenge);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            logger.info("Webhook verified successfully");
            return Mono.just(challenge);
        } else {
            logger.error("Webhook verification failed: Invalid mode or token");
            return Mono.error(new RuntimeException("Webhook verification failed"));
        }
    }

    private WhatsappMessage createWhatsappMessage(MessageAccess access, String to, MessageType messageType) {

        WhatsappMessage whatsappMessage =
                new WhatsappMessage(PhoneUtil.parse(to).getNumber(), messageType);

        whatsappMessage.setFrom(
                PhoneUtil.parse(access.getUser().getPhoneNumber()).getNumber());

        return whatsappMessage;
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
            return processStatusUpdates(value.getStatuses());
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

        return this.validateProviderIdentifier(appCode, clientCode, phoneNumberId)
                .flatMap(providerIdentifier -> {
                    WhatsappMessage whatsappMessage = new WhatsappMessage(message.getFrom(), message.getType());
                    whatsappMessage.setMessageId(message.getId());
                    whatsappMessage.setPhoneNumberId(phoneNumberId);
                    whatsappMessage.setOutbound(false);
                    whatsappMessage.setStatus(MessageStatus.DELIVERED);

                    if (message.getTimestamp() != null) {
                        long timestamp = Long.parseLong(message.getTimestamp());
                        LocalDateTime dateTime =
                                LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);
                        whatsappMessage.setDeliveredTime(dateTime);
                    }

                    whatsappMessage.setInMessage(message);

                    return this.createInternal(
                            MessageAccess.of(appCode, clientCode, providerIdentifier.getUserId(), Boolean.TRUE),
                            whatsappMessage);
                });
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
        whatsappMessage.setStatus(status.getStatus());

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
}
