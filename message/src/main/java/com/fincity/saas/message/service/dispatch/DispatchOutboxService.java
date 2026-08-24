package com.fincity.saas.message.service.dispatch;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.message.dao.dispatch.DispatchOutboxDAO;
import com.fincity.saas.message.dto.dispatch.DispatchOutbox;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.model.common.MessageAccess;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The durable handoff from this service to the service that owns an event.
 *
 * <p>The provider is told its webhook succeeded as soon as a row here is committed, before the
 * owning service has seen anything. That is the point: entity-processor being down must not make us
 * look unavailable to Meta or Exotel, and their retry windows are finite, so leaning on them would
 * eventually lose events during a long outage.
 *
 * <p>Channel-agnostic. WhatsApp messages and call status updates have the same handoff problem and
 * the same answer, so they share one table, one backoff and one sweeper rather than growing a
 * second copy of each that drifts.
 *
 * <p>Rows are deleted on success rather than flagged, so an empty table is the healthy state and
 * the row count is a usable alert with nothing else to maintain. The counter-example is next door:
 * {@code message_message_webhooks.IS_PROCESSED} is only set when an entire event succeeds, and it
 * sits at tens of thousands unprocessed against a hundred processed, which makes it useless for
 * exactly the job this table does.
 */
@Service
public class DispatchOutboxService {

    private static final Logger logger = LoggerFactory.getLogger(DispatchOutboxService.class);

    private final DispatchOutboxDAO dao;

    /**
     * Past this, a row stops being retried and stays put. Undeliverable is a thing a person needs
     * to see, so it must neither disappear nor spin forever.
     */
    @Value("${message.dispatch.outbox.max-attempts:12}")
    private int maxAttempts;

    @Value("${message.dispatch.outbox.base-backoff-seconds:30}")
    private long baseBackoffSeconds;

    @Value("${message.dispatch.outbox.max-backoff-seconds:3600}")
    private long maxBackoffSeconds;

    public DispatchOutboxService(DispatchOutboxDAO dao) {
        this.dao = dao;
    }

    /**
     * Records a handoff before it is attempted.
     *
     * <p>A duplicate webhook delivery hits the unique key on (meta message id, event type) and is
     * swallowed, so Meta re-sending an event cannot enqueue the same work twice.
     */
    public Mono<DispatchOutbox> enqueue(
            MessageAccess access,
            String ownerService,
            String eventKey,
            DispatchEventType eventType,
            Map<String, Object> payload) {

        DispatchOutbox row = DispatchOutbox.of(ownerService, eventKey, eventType, payload)
                .setAppCode(access.getAppCode())
                .setClientCode(access.getClientCode());

        return this.dao
                .create(row)
                // Only a duplicate is benign, and only because the unique key means the work is
                // already queued. Anything else must propagate: the caller returns 200 to Meta on
                // the strength of this write, so swallowing a failure here loses the message with
                // no retry from any direction.
                .onErrorResume(e -> {
                    if (!isDuplicate(e)) return Mono.error(e);
                    logger.debug(
                            "Outbox row for {} event {} already queued, ignoring the redelivery.",
                            eventKey,
                            eventType);
                    return Mono.just(row);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DispatchOutboxService.enqueue"));
    }

    /**
     * A redelivery of an event already queued, which the unique key on (meta message id, event
     * type) rejects.
     *
     * <p>Matched by exception type rather than by driver error code, so it holds if the underlying
     * database ever changes. The whole causal chain is walked because the R2DBC exception usually
     * arrives wrapped by jOOQ.
     */
    private static boolean isDuplicate(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof R2dbcDataIntegrityViolationException
                    || t instanceof DuplicateKeyException
                    || t instanceof DataIntegrityViolationException) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    /** Called once the owning service has accepted the handoff. */
    public Mono<Integer> clear(DispatchChannel channel, String eventKey, DispatchEventType eventType) {
        return this.dao
                .clear(channel, eventKey, eventType)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DispatchOutboxService.clear"));
    }

    /** Called when a dispatch attempt failed, to schedule the next one. */
    public Mono<Integer> recordFailure(ULong id, int currentAttempts, String error) {
        return this.dao
                .recordFailure(id, error, this.nextAttemptAfter(currentAttempts))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "DispatchOutboxService.recordFailure"));
    }

    public Mono<List<DispatchOutbox>> readDue(int limit) {
        return this.dao.readDue(LocalDateTime.now(ZoneOffset.UTC), this.maxAttempts, limit);
    }

    public Mono<Integer> countExhausted() {
        return this.dao.countExhausted(this.maxAttempts);
    }

    /**
     * Exponential backoff, capped. Doubling from 30s reaches the hour ceiling in about seven
     * attempts, so twelve attempts covers roughly six hours of consumer downtime before a row is
     * left for a person.
     */
    private LocalDateTime nextAttemptAfter(int currentAttempts) {
        long seconds = Math.min(
                this.maxBackoffSeconds, this.baseBackoffSeconds * (1L << Math.min(currentAttempts, 20)));
        return LocalDateTime.now(ZoneOffset.UTC).plus(Duration.ofSeconds(seconds));
    }
}
