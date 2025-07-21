package com.fincity.saas.message.service.call.event;

import com.fincity.saas.message.model.event.MessageServerEvent;
import com.fincity.saas.message.service.event.AbstractServerSentEventService;
import com.fincity.saas.message.util.IClassConvertor;
import java.time.LocalDateTime;
import java.util.UUID;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CallEventService extends AbstractServerSentEventService {

    public static final String EVENT_TYPE_MAKE_CALL = "MAKE_CALL";
    public static final String EVENT_TYPE_INCOMING_CALL = "INCOMING_CALL";
    public static final String EVENT_TYPE_CALL_STATUS = "CALL_STATUS";
    public static final String EVENT_TYPE_PASSTHRU_CALLBACK = "PASSTHRU_CALLBACK";

    public Mono<Void> sendCallEvent(MessageServerEvent event) {
        if (event == null || event.getAppCode() == null || event.getClientCode() == null)
            return Mono.error(new IllegalArgumentException("Event, appCode, and clientCode cannot be null"));

        if (event.getId() == null) event.setId(UUID.randomUUID().toString());

        event.setTimestamp(LocalDateTime.now());

        return super.sendEvent(event);
    }

    public <E extends IClassConvertor> Mono<Void> sendMakeCallEvent(
            String appCode, String clientCode, ULong userId, E data) {
        MessageServerEvent event = new MessageServerEvent()
                .setEventType(EVENT_TYPE_MAKE_CALL)
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId.toBigInteger())
                .setData(data.toMap());
        return this.sendCallEvent(event);
    }

    public <E extends IClassConvertor> Mono<Void> sendIncomingCallEvent(
            String appCode, String clientCode, ULong userId, E data) {
        MessageServerEvent event = new MessageServerEvent()
                .setEventType(EVENT_TYPE_INCOMING_CALL)
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId.toBigInteger())
                .setData(data.toMap());
        return this.sendCallEvent(event);
    }

    public <E extends IClassConvertor> Mono<Void> sendCallStatusEvent(
            String appCode, String clientCode, ULong userId, E data) {
        MessageServerEvent event = new MessageServerEvent()
                .setEventType(EVENT_TYPE_CALL_STATUS)
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId.toBigInteger())
                .setData(data.toMap());
        return this.sendCallEvent(event);
    }

    public <E extends IClassConvertor> Mono<Void> sendPassthruCallbackEvent(
            String appCode, String clientCode, ULong userId, E data) {
        MessageServerEvent event = new MessageServerEvent()
                .setEventType(EVENT_TYPE_PASSTHRU_CALLBACK)
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setUserId(userId.toBigInteger())
                .setData(data.toMap());
        return this.sendCallEvent(event);
    }
}
