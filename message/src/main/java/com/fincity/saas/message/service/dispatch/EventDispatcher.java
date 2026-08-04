package com.fincity.saas.message.service.dispatch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.dto.dispatch.DispatchOutbox;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.model.common.MessageAccess;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Routes a provider event to the service that owns it, through the outbox.
 *
 * <p>The order matters and is the whole design. The outbox row is committed <b>first</b>, and only
 * then is delivery attempted. That is what lets a webhook answer the provider immediately: if the
 * owner is down, or this instance dies mid-dispatch, the row is still on disk and the sweeper picks
 * it up. Attempting delivery first and enqueueing on failure would lose everything in flight during
 * a crash.
 *
 * <p>One dispatcher for every channel. WhatsApp and calls differ in what the payload means and which
 * feign method carries it, both of which are the handler's business, and in nothing this class does.
 */
@Service
public class EventDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(EventDispatcher.class);

    private final DispatchOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Map<String, IDispatchHandler> handlers;

    public EventDispatcher(
            DispatchOutboxService outboxService, ObjectMapper objectMapper, List<IDispatchHandler> handlers) {
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.handlers =
                handlers.stream().collect(Collectors.toMap(IDispatchHandler::registryKey, Function.identity()));
    }

    /**
     * Queues an event and tries to deliver it now.
     *
     * <p>Returns as soon as the row is durable. Delivery runs after and its outcome does not change
     * what the caller sees, because the caller is answering a provider and the provider only needs
     * to know we have the event.
     */
    public Mono<Void> enqueueAndDispatch(
            MessageAccess access,
            String ownerService,
            DispatchEventType eventType,
            String eventKey,
            Object dispatch) {

        if (ownerService == null || ownerService.isBlank()) {
            // Parked, not dropped. An unrouted event is a configuration gap, and it stays
            // recoverable once OWNER_SERVICE is set and the sweeper runs.
            logger.error(
                    "{} event {} has no OWNER_SERVICE for app {} client {}."
                            + " Parking it: set the owner and it will be delivered on the next sweep.",
                    eventType,
                    eventKey,
                    access.getAppCode(),
                    access.getClientCode());
        }

        Map<String, Object> payload = this.asPayload(dispatch);

        return this.outboxService
                .enqueue(access, ownerService, eventKey, eventType, payload)
                .flatMap(row -> this.deliver(access, row)
                        // Delivery failure is expected and handled by the sweeper, so it must not
                        // propagate into the webhook response.
                        .onErrorResume(e -> Mono.empty()))
                .then();
    }

    /** Attempts one delivery, clearing the outbox row on success and scheduling a retry if not. */
    public Mono<Void> deliver(MessageAccess access, DispatchOutbox row) {

        DispatchChannel channel =
                row.getChannel() == null ? row.getEventType().getChannel() : row.getChannel();

        IDispatchHandler handler = this.handlers.get(IDispatchHandler.key(channel, row.getOwnerService()));

        if (handler == null) {
            String known = String.join(", ", this.handlers.keySet());
            logger.error(
                    "No {} handler for owner service '{}' (known: {}). Event {} stays in the outbox.",
                    channel,
                    row.getOwnerService(),
                    known,
                    row.getEventKey());
            return this.outboxService
                    .recordFailure(
                            row.getId(),
                            row.getAttempts() == null ? 0 : row.getAttempts(),
                            "No " + channel + " handler registered for owner service " + row.getOwnerService())
                    .then();
        }

        return handler.handle(access.getAppCode(), access.getClientCode(), row.getPayload())
                .then(this.outboxService.clear(channel, row.getEventKey(), row.getEventType()))
                .doOnNext(cleared -> logger.debug(
                        "Delivered {} event {} to {}.", channel, row.getEventKey(), row.getOwnerService()))
                .onErrorResume(e -> {
                    logger.warn(
                            "Could not deliver {} event {} to {}, leaving it queued for retry: {}",
                            channel,
                            row.getEventKey(),
                            row.getOwnerService(),
                            e.getMessage());
                    return this.outboxService
                            .recordFailure(
                                    row.getId(), row.getAttempts() == null ? 0 : row.getAttempts(), e.getMessage())
                            .onErrorResume(inner -> {
                                logger.error("Could not even record the dispatch failure.", inner);
                                return Mono.just(0);
                            });
                })
                .then();
    }

    private Map<String, Object> asPayload(Object dispatch) {
        return this.objectMapper.convertValue(dispatch, new TypeReference<Map<String, Object>>() {});
    }
}
