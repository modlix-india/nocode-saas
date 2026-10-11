package com.fincity.saas.message.service.event;

import com.fincity.saas.commons.security.util.SecurityContextUtil;
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

    private static final int MAX_EMIT_SPINS = 1000;

    protected final Map<String, Many<MessageServerEvent>> eventSinks = new ConcurrentHashMap<>();

    public Flux<MessageServerEvent> getEventStream(String appCode, String clientCode, BigInteger userId) {
        return this.getSink(appCode, clientCode, userId).asFlux();
    }

    /**
     * The caller's own stream, whatever user id they asked for.
     *
     * <p>The controller used to pass {@code userId} straight through from a request parameter, which
     * meant any authenticated caller could read another user's stream by editing the URL. Call events
     * carry customer numbers and call state, so that was worth closing.
     *
     * <p>The parameter is still accepted on the endpoint rather than removed, because the UI already
     * sends it and a 400 would break those pages for no gain. It is simply no longer believed: the
     * id comes from the token. A caller asking for their own id sees no change, and a caller asking
     * for somebody else's now gets their own.
     */
    public Flux<MessageServerEvent> getEventStreamForCurrentUser(String appCode, String clientCode) {
        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMapMany(ca -> this.getEventStream(appCode, clientCode, ca.getUser().getId()));
    }

    public Mono<Void> sendEvent(MessageServerEvent event) {
        if (event == null || event.getAppCode() == null || event.getClientCode() == null)
            return Mono.error(new IllegalArgumentException("Event, appCode, and clientCode cannot be null"));

        Many<MessageServerEvent> sink = getSink(event.getAppCode(), event.getClientCode(), event.getUserId());

        return Mono.fromRunnable(() -> {
            logger.debug("Sending event: {}", event);
            // A sink takes one emitter at a time, and two webhooks for one tenant can land together.
            // The loser used to be dropped with an error; a short spin lets it through instead.
            Sinks.EmitResult result = sink.tryEmitNext(event);
            for (int spins = 0; result == Sinks.EmitResult.FAIL_NON_SERIALIZED && spins < MAX_EMIT_SPINS; spins++) {
                Thread.onSpinWait();
                result = sink.tryEmitNext(event);
            }
            // Nobody watching is normal for a stream that drops rather than buffers.
            if (result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) logger.debug("No listener for event: {}", event);
            else if (result.isFailure()) logger.error("Failed to emit event: {}", result);
        });
    }

    private Many<MessageServerEvent> getSink(String appCode, String clientCode, BigInteger userId) {
        return eventSinks.computeIfAbsent(getEventSinkKey(appCode, clientCode, userId), k -> this.newSink());
    }

    /**
     * The sink behind one stream key.
     *
     * <p>Overridable because a buffered multicast sink suits a stream with one listener and is wrong
     * for a shared one: it buffers while nobody listens, and once its last subscriber cancels it
     * terminates, so every later subscriber on that key gets nothing.
     */
    protected Many<MessageServerEvent> newSink() {
        return Sinks.many().multicast().onBackpressureBuffer();
    }

    protected String getEventSinkKey(String appCode, String clientCode, BigInteger userId) {
        return appCode + ":" + clientCode + ":" + userId;
    }
}
