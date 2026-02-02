package com.fincity.saas.message.service.event;

import com.fincity.saas.message.model.event.MessageServerEvent;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

public abstract class AbstractServerSentEventService {

    private static final Logger logger = LoggerFactory.getLogger(AbstractServerSentEventService.class);

    protected final Map<String, Many<MessageServerEvent>> eventSinks = new ConcurrentHashMap<>();

    public Flux<MessageServerEvent> getEventStream(String appCode, String clientCode, BigInteger userId) {
        return this.getSink(appCode, clientCode, userId).asFlux();
    }

    public Mono<Void> sendEvent(MessageServerEvent event) {
        if (event == null || event.getAppCode() == null || event.getClientCode() == null)
            return Mono.error(new IllegalArgumentException("Event, appCode, and clientCode cannot be null"));

        Many<MessageServerEvent> sink = getSink(event.getAppCode(), event.getClientCode(), event.getUserId());

        return Mono.fromRunnable(() -> {
            logger.debug("Sending event: {}", event);
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) logger.error("Failed to emit event: {}", result);
        });
    }

    private Many<MessageServerEvent> getSink(String appCode, String clientCode, BigInteger userId) {
        return eventSinks.computeIfAbsent(
                getEventSinkKey(appCode, clientCode, userId),
                k -> Sinks.many().multicast().onBackpressureBuffer());
    }

    protected String getEventSinkKey(String appCode, String clientCode, BigInteger userId) {
        return appCode + ":" + clientCode + ":" + userId;
    }
}
