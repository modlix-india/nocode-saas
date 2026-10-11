package com.fincity.saas.message.service.dispatch.handler;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.message.service.dispatch.IDispatchHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Hands events to core, for numbers and calls whose {@code OWNER_SERVICE} is {@code core}.
 *
 * <p>The consumer is not a service with a typed endpoint but a tenant's own KIRun: the event is
 * raised on the same RabbitMQ events exchange core and security already publish to, and core's
 * {@code EventsQueListener} runs the EventAction of the same name for the number's app and client.
 * So a CRM built on storages takes over a number by setting its owner, with no change here.
 *
 * <p>The data is the dispatch the entity-processor handlers send, unchanged, plus the four fields a
 * function needs to act on it without parsing anything: {@code appCode}, {@code clientCode}, {@code
 * numberId} and {@code eventType}.
 *
 * <p>Delivered means the broker took it. Whether the EventAction then succeeds is core's business:
 * an event with no EventAction defined is dropped there, silently, as every core event is.
 *
 * <p>Must throw rather than swallow, like every handler: the outbox row survives only if the error
 * reaches the dispatcher.
 */
public abstract class CoreEventHandler implements IDispatchHandler {

    public static final String SERVICE_NAME = "core";

    public static final String APP_CODE = "appCode";
    public static final String CLIENT_CODE = "clientCode";
    public static final String NUMBER_ID = "numberId";
    public static final String EVENT_TYPE = "eventType";

    private final EventCreationService eventCreationService;

    protected CoreEventHandler(EventCreationService eventCreationService) {
        this.eventCreationService = eventCreationService;
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    /** The core event raised, and so the name of the EventAction that handles it. */
    public abstract String getCoreEventName();

    /** What happened, when the dispatch itself does not say. */
    protected abstract String getDefaultEventType();

    /** Which of the tenant's numbers this came in on, or null where this service holds no numbers. */
    protected abstract Object getNumberId(Map<String, Object> payload);

    @Override
    public Mono<Void> handle(String appCode, String clientCode, Map<String, Object> payload) {

        Map<String, Object> data = this.toEventData(appCode, clientCode, payload);

        return this.eventCreationService
                .createEvent(new EventQueObject()
                        .setEventName(this.getCoreEventName())
                        .setAppCode(appCode)
                        .setClientCode(clientCode)
                        .setData(data))
                .flatMap(sent -> Boolean.TRUE.equals(sent)
                        ? Mono.just(sent)
                        : Mono.<Boolean>error(new IllegalStateException(
                                "Core event " + this.getCoreEventName() + " was not published.")))
                .then(Mono.defer(() -> this.afterDelivery(appCode, clientCode, data)));
    }

    /** The event's data: the dispatch as it is, plus where it belongs and what it is. */
    public Map<String, Object> toEventData(String appCode, String clientCode, Map<String, Object> payload) {

        Map<String, Object> data = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);

        Object eventType = data.get(EVENT_TYPE);

        data.put(APP_CODE, appCode);
        data.put(CLIENT_CODE, clientCode);
        data.put(NUMBER_ID, this.getNumberId(data));
        data.put(
                EVENT_TYPE,
                eventType == null || eventType.toString().isBlank() ? this.getDefaultEventType() : eventType);

        return data;
    }

    /**
     * Anything owed once core has the event.
     *
     * <p>Must not fail: the event is already on the exchange, so an error here would make the
     * dispatcher keep the row and publish the same event again.
     */
    protected Mono<Void> afterDelivery(String appCode, String clientCode, Map<String, Object> data) {
        return Mono.empty();
    }
}
