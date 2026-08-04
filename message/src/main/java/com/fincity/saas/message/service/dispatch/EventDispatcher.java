package com.fincity.saas.message.service.message.provider.whatsapp.dispatch;

import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappOutbox;
import com.fincity.saas.message.enums.message.provider.whatsapp.WhatsappOutboxEventType;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappInboundDispatch;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappOutboxService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Routes a WhatsApp event to the service that owns the number it arrived on, through the outbox.
 *
 * <p>The order matters and is the whole design. The outbox row is committed <b>first</b>, and only
 * then is delivery attempted. That is what lets the webhook answer Meta 200 immediately: if the
 * owner is down, or this instance dies mid-dispatch, the row is still on disk and the sweeper picks
 * it up. Attempting delivery first and enqueueing on failure would lose everything in flight during
 * a crash.
 */
@Service
public class WhatsappInboundDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(WhatsappInboundDispatcher.class);

    private final WhatsappOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Map<String, IWhatsappInboundHandler> handlers;

    public WhatsappInboundDispatcher(
            WhatsappOutboxService outboxService,
            ObjectMapper objectMapper,
            List<IWhatsappInboundHandler> handlers) {
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(IWhatsappInboundHandler::getServiceName, Function.identity()));
    }

    /**
     * Queues an event and tries to deliver it now.
     *
     * <p>Returns as soon as the row is durable. Delivery runs after and its outcome does not change
     * what the caller sees, because the caller is answering Meta and Meta only needs to know we
     * have the message.
     */
    public Mono<Void> enqueueAndDispatch(
            MessageAccess access,
            String ownerService,
            WhatsappOutboxEventType eventType,
            WhatsappInboundDispatch dispatch) {

        if (ownerService == null || ownerService.isBlank()) {
            // Parked, not dropped. An unrouted number is a configuration gap, and the message is
            // still recoverable once OWNER_SERVICE is set and the sweeper runs.
            logger.error(
                    "WhatsApp message {} arrived on a number with no OWNER_SERVICE for app {} client {}."
                            + " Parking it: set the owner and it will be delivered on the next sweep.",
                    dispatch.getMetaMessageId(),
                    access.getAppCode(),
                    access.getClientCode());
        }

        Map<String, Object> payload =
                this.objectMapper.convertValue(dispatch, new TypeReference<Map<String, Object>>() {});

        return this.outboxService
                .enqueue(access, ownerService, dispatch.getMetaMessageId(), eventType, payload)
                .flatMap(row -> this.deliver(access, row, dispatch)
                        // Delivery failure is expected and handled by the sweeper, so it must not
                        // propagate into the webhook response.
                        .onErrorResume(e -> Mono.empty()))
                .then();
    }

    /** Attempts one delivery, clearing the outbox row on success and scheduling a retry if not. */
    public Mono<Void> deliver(MessageAccess access, WhatsappOutbox row, WhatsappInboundDispatch dispatch) {

        IWhatsappInboundHandler handler = this.handlers.get(row.getOwnerService());

        if (handler == null) {
            String known = String.join(", ", this.handlers.keySet());
            logger.error(
                    "No handler for owner service '{}' (known: {}). WhatsApp message {} stays in the outbox.",
                    row.getOwnerService(),
                    known,
                    row.getMetaMessageId());
            return this.outboxService
                    .recordFailure(
                            row.getId(),
                            row.getAttempts() == null ? 0 : row.getAttempts(),
                            "No handler registered for owner service " + row.getOwnerService())
                    .then();
        }

        return handler.handle(access.getAppCode(), access.getClientCode(), dispatch)
                .then(this.outboxService.clear(row.getMetaMessageId(), row.getEventType()))
                .doOnNext(cleared -> logger.debug(
                        "Delivered WhatsApp message {} to {}.", row.getMetaMessageId(), row.getOwnerService()))
                .onErrorResume(e -> {
                    logger.warn(
                            "Could not deliver WhatsApp message {} to {}, leaving it queued for retry: {}",
                            row.getMetaMessageId(),
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

    /** Rebuilds the dispatch body from a queued row, for the sweeper to retry. */
    public WhatsappInboundDispatch dispatchOf(WhatsappOutbox row) {
        return this.objectMapper.convertValue(row.getPayload(), WhatsappInboundDispatch.class);
    }
}
