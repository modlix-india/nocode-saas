package com.fincity.saas.message.controller;

import com.fincity.saas.message.model.event.MessageServerEvent;
import com.fincity.saas.message.service.call.event.CallEventService;
import java.math.BigInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/message/events")
public class ServerSentEventController {

    private final CallEventService eventService;

    @Autowired
    public ServerSentEventController(CallEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MessageServerEvent> getEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId) {

        return eventService.getEventStream(appCode, clientCode, userId);
    }

    @GetMapping(value = "/call/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MessageServerEvent> getCallEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId) {

        return eventService.getEventStream(appCode, clientCode, userId).filter(event -> {
            String eventType = event.getEventType();
            return eventType != null
                    && (eventType.equals(CallEventService.EVENT_TYPE_MAKE_CALL)
                            || eventType.equals(CallEventService.EVENT_TYPE_INCOMING_CALL)
                            || eventType.equals(CallEventService.EVENT_TYPE_CALL_STATUS)
                            || eventType.equals(CallEventService.EVENT_TYPE_PASSTHRU_CALLBACK));
        });
    }

    @GetMapping(value = "/call/makeCall/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MessageServerEvent> getMakeCallEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId) {

        return eventService
                .getEventStream(appCode, clientCode, userId)
                .filter(event -> CallEventService.EVENT_TYPE_MAKE_CALL.equals(event.getEventType()));
    }

    @GetMapping(value = "/call/incomingCall/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MessageServerEvent> getIncomingCallEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId) {

        return eventService
                .getEventStream(appCode, clientCode, userId)
                .filter(event -> CallEventService.EVENT_TYPE_INCOMING_CALL.equals(event.getEventType()));
    }

    @GetMapping(value = "/call/callStatus/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MessageServerEvent> getCallStatusEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId) {

        return eventService
                .getEventStream(appCode, clientCode, userId)
                .filter(event -> CallEventService.EVENT_TYPE_CALL_STATUS.equals(event.getEventType()));
    }

    @GetMapping(value = "/call/passthruCallback/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MessageServerEvent> getPassthruCallbackEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId) {

        return eventService
                .getEventStream(appCode, clientCode, userId)
                .filter(event -> CallEventService.EVENT_TYPE_PASSTHRU_CALLBACK.equals(event.getEventType()));
    }
}
