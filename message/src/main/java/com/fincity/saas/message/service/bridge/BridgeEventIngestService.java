package com.fincity.saas.message.service.bridge;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.bridge.BridgeEvent;
import com.fincity.saas.message.model.request.bridge.BridgeEventsRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappInboundDispatch;
import com.fincity.saas.message.service.dispatch.EventDispatcher;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Takes inbound messages and receipts from a bridge and hands them to the service that owns the
 * number.
 *
 * <p>The chain is: bridge local outbox, across the region, to here, into {@code
 * message_dispatch_outbox}, and on to entity-processor. Two outboxes, deliberately, covering
 * different hops with different failure modes: the first covers a 200ms cross-region link that will
 * break, the second already existed and is shared with calls.
 *
 * <p><b>The response is the acknowledgement, and it must not be sent early.</b> The bridge deletes
 * its only copy of an event the moment this returns 2xx, and WhatsApp will not redeliver it, so
 * answering on receipt rather than on commit loses the message outright with nothing anywhere to
 * replay it from. {@link EventDispatcher#enqueueAndDispatch} completes once the outbox row is
 * durable, which is exactly the guarantee needed; delivery happens after and its outcome
 * deliberately does not change what the bridge sees.
 */
@Service
public class BridgeEventIngestService {

    private static final Logger logger = LoggerFactory.getLogger(BridgeEventIngestService.class);

    private final WhatsappPhoneNumberDAO sessionDao;
    private final EventDispatcher eventDispatcher;

    public BridgeEventIngestService(WhatsappPhoneNumberDAO sessionDao, EventDispatcher eventDispatcher) {
        this.sessionDao = sessionDao;
        this.eventDispatcher = eventDispatcher;
    }

    /**
     * Commits a batch.
     *
     * <p>Sequential rather than concurrent, because ordering within one session is worth more than
     * the latency: a status update overtaking its own message is handled downstream by the upsert,
     * but there is no reason to create the situation.
     *
     * <p>An error anywhere fails the whole batch, and that is the right trade. The bridge retries
     * the batch, and the unique key on (channel, event key, event type) makes re-enqueueing anything
     * already committed a no-op. Partial success reported as success is the only outcome that loses
     * data.
     */
    public Mono<Integer> ingest(BridgeEventsRequest request) {

        if (request.getEvents() == null || request.getEvents().isEmpty()) return Mono.just(0);

        return Flux.fromIterable(request.getEvents())
                .concatMap(event -> this.ingestOne(request.getInstanceId(), event))
                .reduce(0, Integer::sum)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeEventIngestService.ingest"));
    }

    private Mono<Integer> ingestOne(String instanceId, BridgeEvent event) {

        if (event.getSessionId() == null || event.getMessageId() == null) {
            // Unusable rather than merely unroutable: with no message id there is no idempotency key,
            // so accepting it would let a redelivery duplicate the message. Dropped and counted,
            // because failing the batch would wedge the queue behind a row that can never succeed.
            logger.error(
                    "Bridge {} sent an event with no session id or message id. Dropping it: {}", instanceId, event);
            return Mono.just(0);
        }

        return this.sessionDao
                .getBySessionIdInternal(event.getSessionId())
                .switchIfEmpty(Mono.defer(() -> {
                    // Not an error to the bridge. Retrying will not conjure the session row, so
                    // failing the batch would stall every other event behind an unresolvable one.
                    // Reconciliation is what surfaces this properly, on the next registration.
                    logger.error(
                            "Bridge {} sent event {} for unknown session {}. Dropping it;"
                                    + " reconciliation will report the session as a stray.",
                            instanceId,
                            event.getMessageId(),
                            event.getSessionId());
                    return Mono.empty();
                }))
                .flatMap(session -> this.dispatch(instanceId, event, session))
                .thenReturn(1)
                .defaultIfEmpty(0);
    }

    private Mono<Void> dispatch(String instanceId, BridgeEvent event, WhatsappPhoneNumber session) {

        if (session.getBridgeInstanceId() != null && !session.getBridgeInstanceId().equals(instanceId))
            // Accepted, then shouted about. The message is real and dropping it helps nobody, but an
            // instance sending traffic for a session assigned elsewhere is the signature of two
            // processes on one device store, which is unrecoverable and needs a person now.
            logger.error(
                    "STRAY TRAFFIC: bridge {} sent event {} for session {}, which is assigned to {}."
                            + " Accepting the message, but check for two processes on one device store.",
                    instanceId,
                    event.getMessageId(),
                    event.getSessionId(),
                    session.getBridgeInstanceId());

        MessageAccess access = MessageAccess.of(session.getAppCode(), session.getClientCode(), Boolean.TRUE);

        DispatchEventType eventType =
                event.getEventType() == null ? DispatchEventType.INBOUND_MESSAGE : event.getEventType();

        WhatsappInboundDispatch dispatch = new WhatsappInboundDispatch()
                .setMetaMessageId(event.getMessageId())
                .setEventType(eventType.name())
                .setProductId(session.getProductId() == null ? null : session.getProductId().toBigInteger())
                .setWhatsappPhoneNumberId(session.getId() == null ? null : session.getId().toBigInteger())
                // No WABA on this path and there never will be. Left null rather than faked, so the
                // absence is visible in the stored payload instead of looking like a lookup that
                // failed.
                .setWhatsappBusinessAccountId(null)
                .setWhatsappPhoneNumber(session.getDisplayPhoneNumber())
                .setCustomerWaId(event.getCustomerWaId())
                .setCustomerPhoneNumber(event.getCustomerPhoneNumber())
                .setFrom(event.getFrom())
                .setTo(event.getTo())
                // Passed through exactly as the bridge sent them. entity-processor upper-cases before
                // resolving its enum, so casing is tolerated at this hop; it is on the hop AFTER it,
                // where entity-processor re-serialises lowercase through @JsonValue, that the deal
                // profile's ~150 expressions depend on the exact string. Nothing here should
                // normalise, because normalising is how a value gets changed on the way past.
                .setMessageType(event.getMessageType())
                .setMessageStatus(event.getMessageStatus())
                .setOccurredAt(toUtc(event.getOccurredAt()))
                .setBodyText(event.getBodyText())
                .setOutbound(event.getOutbound() != null && event.getOutbound())
                // The session this arrived on. Available here all along - it is the row we just
                // looked the event up by - and simply never copied across, which left every inbound
                // row with a null and the pacing layer's reply count reading zero.
                .setBridgeSessionId(session.getCode())
                .setMediaFileDetail(event.getMediaFileDetail())
                .setMediaMimeType(event.getMediaMimeType())
                .setMediaFileName(event.getMediaFileName())
                .setMediaSize(event.getMediaSize())
                .setMediaDurationSeconds(event.getMediaDurationSeconds())
                .setMediaIsVoiceNote(event.getMediaIsVoiceNote())
                .setMediaError(event.getMediaError())
                .setReactionToMessageId(event.getReactionToMessageId());

        return this.eventDispatcher.enqueueAndDispatch(
                access, session.getOwnerService(), eventType, event.getMessageId(), dispatch);
    }

    /**
     * The bridge sends RFC 3339 with an offset; the rest of this chain speaks UTC LocalDateTime.
     *
     * <p>Converted explicitly against {@code ZoneOffset.UTC} rather than the system default, which is
     * UTC on every server and IST on a developer machine. Using the default would put every message
     * five and a half hours out of place in the thread, only locally, which is the kind of bug that
     * gets chased for an afternoon.
     */
    private static LocalDateTime toUtc(Instant instant) {
        return instant == null
                ? LocalDateTime.now(ZoneOffset.UTC)
                : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
