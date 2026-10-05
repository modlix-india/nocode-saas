package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.repository.ListFunctionRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.ConversionEventDAO;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.ConversionActionSource;
import com.fincity.saas.entity.processor.enums.ConversionEventStatus;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorConversionEventsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Outbox accessor for {@link ConversionEvent}. Enqueues new events on stage
 * transitions (idempotent via the UNIQUE {@code (ticket_id, mapping_id)} index)
 * and provides {@code findDispatchable} + {@code markStatus} for the worker
 * dispatcher.
 */
@Service
public class ConversionEventService
        extends BaseUpdatableService<EntityProcessorConversionEventsRecord, ConversionEvent, ConversionEventDAO>
        implements IRepositoryProvider {

    private static final ClassSchema classSchema =
            ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());

    private static final Logger logger = LoggerFactory.getLogger(ConversionEventService.class);

    private static final long BACKOFF_BASE_SECONDS = 30L;
    private static final long BACKOFF_CAP_SECONDS = 3600L;

    /**
     * How many times a retryable failure is retried before it is treated as permanent. See
     * {@link #markFailed} for why a ceiling is needed at all.
     */
    private static final int MAX_ATTEMPTS = 50;

    private final List<ReactiveFunction> functions = new ArrayList<>();

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    /**
     * Builds the outbox row for a (ticket, mapping) pair. Returns existing row if
     * one is already present so the caller can decide whether to re-fire (status
     * SENT → skip; status FAILED → already eligible for retry, no enqueue needed).
     */
    public Mono<ConversionEvent> enqueue(
            ProcessorAccess access, Ticket ticket, ConversionActionMapping mapping, ConversionActionSource source) {

        return this.dao.findByTicketAndMapping(ticket.getId(), mapping.getId())
                .switchIfEmpty(Mono.defer(() -> this.persistFresh(access, ticket, mapping, source)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConversionEventService.enqueue"));
    }

    private Mono<ConversionEvent> persistFresh(
            ProcessorAccess access, Ticket ticket, ConversionActionMapping mapping, ConversionActionSource source) {

        ConversionEvent event = new ConversionEvent()
                .setTicketId(ticket.getId())
                .setMappingId(mapping.getId())
                .setEventId(buildEventId(ticket, mapping))
                .setEventName(mapping.getEventName())
                .setCampaignPlatform(mapping.getCampaignPlatform())
                .setActionSource(source)
                .setPayloadSnapshot(buildPayloadSnapshot(ticket, mapping, source))
                .setStatus(ConversionEventStatus.PENDING)
                .setAttemptCount(0);
        return super.createInternal(access, event);
    }

    /**
     * Deterministic event_id: same ticket + mapping always produces the same id,
     * so Meta's dedupe window also catches accidental double-fires.
     */
    private String buildEventId(Ticket ticket, ConversionActionMapping mapping) {
        return ticket.getCode() + "-" + mapping.getCode();
    }

    /**
     * Captures the per-ticket bits the dispatcher will hash + send. Stored as JSON
     * so the dispatcher does NOT re-read a possibly-updated ticket later.
     */
    private Map<String, Object> buildPayloadSnapshot(
            Ticket ticket, ConversionActionMapping mapping, ConversionActionSource source) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("email", ticket.getEmail());
        snap.put("phone", ticket.getPhoneNumber());
        snap.put("dialCode", ticket.getDialCode());
        snap.put("ticketCode", ticket.getCode());
        snap.put("source", source.name());
        snap.put("value", mapping.getDefaultValue());
        snap.put("currency", mapping.getCurrency());
        if (ticket.getAdData() != null) snap.put("adData", ticket.getAdData());
        return snap;
    }

    public Flux<ConversionEvent> findDispatchable(int batchSize) {
        return this.dao.findDispatchable(batchSize);
    }

    public Mono<Void> markSent(ConversionEvent event, String message) {
        return this.dao
                .markStatus(
                        event.getId(),
                        ConversionEventStatus.SENT,
                        message,
                        LocalDateTime.now(),
                        null,
                        event.getAttemptCount() == null ? 1 : event.getAttemptCount() + 1)
                .then();
    }

    /**
     * Records a retryable failure — unless it has been retried too many times, in which case it
     * stops being retryable.
     *
     * <h2>Why there is a cap</h2>
     *
     * <p>{@code findDispatchable} selects {@code STATUS IN (PENDING, FAILED)} and {@link #backoff}
     * tops out at one hour, so before this cap a FAILED row was retried hourly for as long as it
     * existed. Terminal classification at the dispatcher handles the causes we have recognised; this
     * is the backstop for the ones we have not.
     *
     * <p>It is needed because the gap is not hypothetical. On production 2026-10-05, alongside the
     * 49 rows with a missing pixel id, there were 10 rows failing on a Meta 400 and one on a Google
     * click-window rejection — all permanent, none recognised as such, between them part of 36,162
     * wasted attempts over 98 days. Each attempt makes live platform calls, and sixty of them
     * arriving together each hour is what turned a dead letter into an outage.
     *
     * <p>Fifty attempts is deliberately generous: with this backoff that is roughly two days of
     * retries, far longer than any outage or token refresh a conversion should have to survive. If
     * something is still failing after two days it is not waiting on a transient condition.
     */
    public Mono<Void> markFailed(ConversionEvent event, String message) {
        int next = event.getAttemptCount() == null ? 1 : event.getAttemptCount() + 1;

        if (next >= MAX_ATTEMPTS) {
            logger.warn(
                    "Conversion event {} has failed {} times and will no longer be retried: {}."
                            + " Marking SKIPPED; the row is kept and can be requeued once the cause is"
                            + " corrected.",
                    event.getId(),
                    next,
                    message);
            return this.markSkipped(
                    event, "Gave up after " + next + " attempts. Last error: " + message);
        }

        return this.dao
                .markStatus(
                        event.getId(),
                        ConversionEventStatus.FAILED,
                        message,
                        null,
                        LocalDateTime.now().plus(backoff(next)),
                        next)
                .then();
    }

    /**
     * Terminal: marks the event SKIPPED with no retry. Use for conditions that
     * cannot self-correct on the next tick (e.g. ticket has no platform attribution,
     * referenced mapping/ticket deleted, dispatcher unregistered for platform).
     */
    public Mono<Void> markSkipped(ConversionEvent event, String message) {
        return this.dao.markSkipped(event.getId(), message).then();
    }

    /**
     * Puts a campaign's SKIPPED conversions back in the retry pool, for use once the cause of the
     * skip has been corrected.
     *
     * <p>Terminal classification is deliberately one-way — nothing re-selects a SKIPPED row — so
     * without this, correcting a campaign's pixel id would fix future conversions and silently
     * leave the historical ones undelivered. That is the whole reason skipped rows are kept rather
     * than deleted.
     *
     * <p>Returns how many rows were moved, so the caller can tell "fixed, 49 queued" from
     * "nothing to do".
     */
    public Mono<Integer> requeueSkippedForCampaign(ULong campaignId) {
        return this.dao
                .countSkippedForCampaign(campaignId)
                .flatMap(count -> count == 0
                        ? Mono.just(0)
                        : this.dao
                                .requeueSkippedForCampaign(
                                        campaignId, "Requeued after the campaign configuration was corrected")
                                .doOnNext(moved -> logger.info(
                                        "Requeued {} skipped conversion event(s) for campaign {}", moved, campaignId)));
    }

    /** Exponential backoff with cap: 30s, 60s, 120s, ..., max 1h. */
    private static Duration backoff(int attempt) {
        long seconds = Math.min(BACKOFF_CAP_SECONDS, BACKOFF_BASE_SECONDS * (1L << Math.min(attempt - 1, 8)));
        return Duration.ofSeconds(seconds);
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {
        return this.defaultSchemaRepositoryFor(ConversionEvent.class, classSchema);
    }
}
