package com.fincity.saas.message.service.message.event;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.model.event.MessageServerEvent;
import com.fincity.saas.message.service.event.AbstractServerSentEventService;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class MessageEventService extends AbstractServerSentEventService {

    private static final String MESSAGE_EVENT_TYPE = "MESSAGE";
    private static final String MESSAGE_STATUS_EVENT_TYPE = "MESSAGE_STATUS";
    private static final String INCOMING_MESSAGE_EVENT_TYPE = "INCOMING_MESSAGE";

    public Mono<Void> sendMessageEvent(String appCode, String clientCode, ULong userId, WhatsappMessage message) {
        MessageServerEvent event = MessageServerEvent.of(MESSAGE_EVENT_TYPE, message.toMap())
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId != null ? userId.toBigInteger() : null);

        return this.sendEvent(event)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageEventService.sendMessageEvent"));
    }

    public Mono<Void> sendMessageStatusEvent(String appCode, String clientCode, ULong userId, WhatsappMessage message) {
        MessageServerEvent event = MessageServerEvent.of(MESSAGE_STATUS_EVENT_TYPE, message.toMap())
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId != null ? userId.toBigInteger() : null);

        return this.sendEvent(event)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageEventService.sendMessageStatusEvent"));
    }

    public Mono<Void> sendIncomingMessageEvent(
            String appCode, String clientCode, ULong userId, WhatsappMessage message) {
        MessageServerEvent event = MessageServerEvent.of(INCOMING_MESSAGE_EVENT_TYPE, message.toMap())
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId != null ? userId.toBigInteger() : null);

        return this.sendEvent(event)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageEventService.sendIncomingMessageEvent"));
    }
}
