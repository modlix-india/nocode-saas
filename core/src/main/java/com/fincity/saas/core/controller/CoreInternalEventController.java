package com.fincity.saas.core.controller;

import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Lets another service raise a core event for an app and client, so the app can react with an
 * EventAction. The worker raises {@code FILES_EXPIRED} through here after the files service has
 * removed files under an app's prefix, so the app can mark whatever pointed at them.
 *
 * <p>Unauthenticated, like /eventRuns/internal/cleanupOlderThan: the worker calls core directly
 * through Eureka and carries no token, and nginx keeps internal paths off the public edge. The
 * event is raised with no authentication, so EventsQueListener runs its EventAction under the
 * client's anonymous event-runner context ({@code Authorities._Event_Runner}), the same context a
 * scheduler tick or a message-service hand-off gets.
 *
 * <p>What bounds it is {@link #RAISABLE_EVENTS}: only events a platform service is meant to raise
 * can be raised here. An arbitrary event name would let any caller inside the cluster run any
 * app's event-runner-only functions, so a new name is added here deliberately, with its producer.
 */
@RestController
@RequestMapping("api/core/events/internal")
public class CoreInternalEventController {

    /** Raised by the worker's files-ttl-cleanup job. data: {resourceType, paths}. */
    public static final String FILES_EXPIRED = "FILES_EXPIRED";

    static final Set<String> RAISABLE_EVENTS = Set.of(FILES_EXPIRED);

    private final EventCreationService ecService;

    public CoreInternalEventController(EventCreationService ecService) {
        this.ecService = ecService;
    }

    public record RaiseRequest(String eventName, String appCode, String clientCode, Map<String, Object> data) {
    }

    /**
     * Publishes the event. 400 with {@code false} when a field is missing or the event is not one
     * that may be raised here; otherwise whether it was handed to the queue. That an app has an
     * EventAction for it is not checked: core logs an unhandled event as it does any other.
     */
    @PostMapping("/raise")
    public Mono<ResponseEntity<Boolean>> raise(@RequestBody RaiseRequest request) {

        if (request == null || isBlank(request.eventName()) || isBlank(request.appCode())
                || isBlank(request.clientCode()) || !RAISABLE_EVENTS.contains(request.eventName()))
            return Mono.just(ResponseEntity.badRequest().body(Boolean.FALSE));

        EventQueObject qob = new EventQueObject()
                .setEventName(request.eventName())
                .setAppCode(request.appCode())
                .setClientCode(request.clientCode())
                .setData(request.data() == null ? Map.of() : request.data());

        return this.ecService.createEvent(qob)
                .map(ResponseEntity::ok)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CoreInternalEventController.raise"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
