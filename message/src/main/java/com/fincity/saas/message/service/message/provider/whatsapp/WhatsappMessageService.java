package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappMessagesRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.messages.Message.MessageBuilder;
import com.fincity.saas.message.model.message.whatsapp.messages.TextMessage;
import com.fincity.saas.message.model.message.whatsapp.messages.type.MessageType;
import com.fincity.saas.message.model.request.message.MessageRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.service.message.provider.AbstractMessageProviderService;
import com.fincity.saas.message.util.PhoneUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class WhatsappMessageService
        extends AbstractMessageProviderService<MessageWhatsappMessagesRecord, WhatsappMessage, WhatsappDAO> {

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

    public String getProvider() {
        return ConnectionSubType.WHATSAPP.getProvider();
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
                        .setMessageProvider(this.getProvider())
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

        if (!messageRequest.isValid()) return super.throwMissingParam(MessageRequest.Fields.text);

        String phoneNumberId =
                (String) connection.getConnectionDetails().getOrDefault(WhatsappMessage.Fields.phoneNumberId, null);
        if (phoneNumberId == null) return super.throwMissingParam(WhatsappMessage.Fields.phoneNumberId);

        MessageBuilder messageBuilder =
                MessageBuilder.builder().setTo(messageRequest.getToNumber().getNumber());

        TextMessage textMessage = new TextMessage().setBody(messageRequest.getText());
        var message = messageBuilder.buildTextMessage(textMessage);

        return FlatMapUtil.flatMapMono(
                        () -> super.isValidConnection(connection),
                        vConn -> this.whatsappApiFactory.newBusinessCloudApiFromConnection(connection),
                        (vConn, api) -> api.sendMessage(phoneNumberId, message),
                        (vConn, api, response) -> {
                            WhatsappMessage whatsappMessage =
                                    this.createWhatsappMessage(access, messageRequest, phoneNumberId);

                            whatsappMessage.setMessageId(
                                    response.getMessages().getFirst().getId());
                            whatsappMessage.setMessage(message);
                            whatsappMessage.setMessageResponse(response);
                            return this.createInternal(access, whatsappMessage);
                        },
                        (vConn, api, response, created) ->
                                this.toMessage(created).map(msg -> msg.setConnectionName(connection.getName())),
                        (vConn, api, response, created, msg) -> this.messageEventService
                                .sendMessageEvent(
                                        access.getAppCode(), access.getClientCode(), access.getUserId(), created)
                                .thenReturn(msg))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappMessageService.sendTextMessage"));
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

    private WhatsappMessage createWhatsappMessage(
            MessageAccess access, MessageRequest messageRequest, String phoneNumberId) {

        WhatsappMessage whatsappMessage =
                new WhatsappMessage(messageRequest.getToNumber().getNumber(), MessageType.TEXT);

        whatsappMessage.setPhoneNumberId(phoneNumberId);
        whatsappMessage.setUserId(access.getUserId());
        whatsappMessage.setAppCode(access.getAppCode());
        whatsappMessage.setClientCode(access.getClientCode());
        whatsappMessage.setFrom(
                PhoneUtil.parse(access.getUser().getPhoneNumber()).getNumber());

        return whatsappMessage;
    }
}
