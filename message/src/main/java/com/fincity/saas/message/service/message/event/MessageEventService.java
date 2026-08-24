package com.fincity.saas.message.service.message.event;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.event.MessageServerEvent;
import com.fincity.saas.message.service.event.AbstractServerSentEventService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class MessageEventService extends AbstractServerSentEventService {

    public static final String EVENT_TYPE_MESSAGE = "message";
    public static final String EVENT_TYPE_MESSAGE_STATUS = "messageStatus";
    public static final String EVENT_TYPE_INCOMING_MESSAGE = "incomingMessage";

    public Mono<Void> sendMessageEvent(MessageAccess access, WhatsappMessage message) {
        MessageServerEvent event = MessageServerEvent.of(EVENT_TYPE_MESSAGE, message.toMap())
                .setAppCode(access.getAppCode())
                .setClientCode(access.getClientCode())
                .setUserId(access.getUserId() != null ? access.getUserId().toBigInteger() : null);

        return this.sendEvent(event)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageEventService.sendMessageEvent"));
    }

    public Mono<Void> sendMessageStatusEvent(MessageAccess access, WhatsappMessage message) {
        MessageServerEvent event = MessageServerEvent.of(EVENT_TYPE_MESSAGE_STATUS, message.toMap())
                .setAppCode(access.getAppCode())
                .setClientCode(access.getClientCode())
                .setUserId(access.getUserId() != null ? access.getUserId().toBigInteger() : null);

        return this.sendEvent(event)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageEventService.sendMessageStatusEvent"));
    }

    public Mono<Void> sendIncomingMessageEvent(MessageAccess access, WhatsappMessage message) {
        MessageServerEvent event = MessageServerEvent.of(EVENT_TYPE_INCOMING_MESSAGE, message.toMap())
                .setAppCode(access.getAppCode())
                .setClientCode(access.getClientCode())
                .setUserId(access.getUserId() != null ? access.getUserId().toBigInteger() : null);

        return this.sendEvent(event)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageEventService.sendIncomingMessageEvent"));
    }
}
