package com.fincity.saas.message.controller;

import com.fincity.saas.message.model.event.MessageServerEvent;
import com.fincity.saas.message.service.call.event.CallEventService;
import com.fincity.saas.message.service.message.event.MessageEventService;
import java.math.BigInteger;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/message/events")
@RequiredArgsConstructor
public class ServerSentEventController {

    private final CallEventService callEventService;
    private final MessageEventService messageEventService;

    private static final String INIT_EVENT = "init";

    private static final Set<String> DEFAULT_CALL_EVENTS = Set.of(
            CallEventService.EVENT_TYPE_MAKE_CALL,
            CallEventService.EVENT_TYPE_INCOMING_CALL,
            CallEventService.EVENT_TYPE_CALL_STATUS,
            CallEventService.EVENT_TYPE_PASSTHRU_CALLBACK
    );

    private static final Set<String> DEFAULT_MESSAGE_EVENTS = Set.of(
            MessageEventService.EVENT_TYPE_MESSAGE,
            MessageEventService.EVENT_TYPE_MESSAGE_STATUS,
            MessageEventService.EVENT_TYPE_INCOMING_MESSAGE
    );

    // ─── Filter Helpers ──────────────────────────────────────────────────────────

    private boolean isMetaEvent(ServerSentEvent<MessageServerEvent> sse) {
        return INIT_EVENT.equals(sse.event()) || sse.comment() != null;
    }

    private Flux<ServerSentEvent<MessageServerEvent>> filterByEventTypes(
            Flux<ServerSentEvent<MessageServerEvent>> stream,
            Set<String> allowedEvents) {

        return stream.filter(sse -> {
            if (isMetaEvent(sse))
                return true;

            MessageServerEvent event = sse.data();
            return event != null && allowedEvents.contains(event.getEventType());
        });
    }

    // ─── Generic Raw Stream (no filters) ───────────────────────────────────────

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<MessageServerEvent>> getEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId) {
        return callEventService.getEventStream(appCode, clientCode, userId);
    }

    // ─── Consolidated Call Stream ──────────────────────────────────────────────

    @GetMapping(value = "/call/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<MessageServerEvent>> getCallEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId,
            @RequestParam(value = "types", required = false) Set<String> types) {

        if (types != null && !types.isEmpty()) {
            if (!DEFAULT_CALL_EVENTS.containsAll(types)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event types: " + types);
            }
            return filterByEventTypes(callEventService.getEventStream(appCode, clientCode, userId), types);
        }

        return filterByEventTypes(callEventService.getEventStream(appCode, clientCode, userId), DEFAULT_CALL_EVENTS);
    }

    // ─── Consolidated Message Stream ───────────────────────────────────────────

    @GetMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<MessageServerEvent>> getMessageEventStream(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestParam("userId") BigInteger userId,
            @RequestParam(value = "types", required = false) Set<String> types) {

        if (types != null && !types.isEmpty()) {
            if (!DEFAULT_MESSAGE_EVENTS.containsAll(types)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event types: " + types);
            }
            return filterByEventTypes(messageEventService.getEventStream(appCode, clientCode, userId), types);
        }

        return filterByEventTypes(messageEventService.getEventStream(appCode, clientCode, userId), DEFAULT_MESSAGE_EVENTS);
    }
}
