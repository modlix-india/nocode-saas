package com.fincity.saas.message.service.event;

import com.fincity.saas.message.model.event.MessageServerEvent;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

public abstract class AbstractServerSentEventService {

    private static final Logger logger = LoggerFactory.getLogger(AbstractServerSentEventService.class);

    private static final String INIT_EVENT = "init";
    private static final String KEEPALIVE_COMMENT = "keepalive";

    protected final Map<String, Many<MessageServerEvent>> eventSinks = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<MessageServerEvent>> getEventStream(String appCode, String clientCode,
            BigInteger userId) {
        String key = getEventSinkKey(appCode, clientCode, userId);

        Flux<ServerSentEvent<MessageServerEvent>> eventStream = this.getSink(appCode, clientCode, userId).asFlux()
                .map(this::buildEvent)
                .doFinally(signal -> {
                    eventSinks.computeIfPresent(key, (k, existingSink) -> {
                        if (existingSink.currentSubscriberCount() == 0) {
                            return null;
                        }
                        return existingSink;
                    });
                });

        Flux<ServerSentEvent<MessageServerEvent>> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(tick -> buildHeartbeat());

        return Flux.concat(Flux.just(buildEvent(MessageServerEvent.of(INIT_EVENT))), Flux.merge(eventStream, heartbeat));
    }

    private ServerSentEvent<MessageServerEvent> buildEvent(MessageServerEvent event) {
        return ServerSentEvent.<MessageServerEvent>builder()
                .id(event.getId() != null ? event.getId() : UUID.randomUUID().toString())
                .event(event.getEventType())
                .data(event)
                .build();
    }

    private ServerSentEvent<MessageServerEvent> buildHeartbeat() {
        return ServerSentEvent.<MessageServerEvent>builder()
                .comment(KEEPALIVE_COMMENT)
                .build();
    }

    private void validateEvent(MessageServerEvent event) {
        if (event == null || event.getAppCode() == null || event.getClientCode() == null) {
            throw new IllegalArgumentException("Event, appCode, and clientCode cannot be null");
        }
    }

    public Mono<Void> sendEvent(MessageServerEvent event) {
        return Mono.fromRunnable(() -> {
            validateEvent(event);

            Many<MessageServerEvent> sink = getSink(event.getAppCode(), event.getClientCode(), event.getUserId());

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
