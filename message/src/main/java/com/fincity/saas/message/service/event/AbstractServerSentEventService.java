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
