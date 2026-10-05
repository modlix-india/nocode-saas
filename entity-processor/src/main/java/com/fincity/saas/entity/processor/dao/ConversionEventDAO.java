package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorConversionEvents.ENTITY_PROCESSOR_CONVERSION_EVENTS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
import com.fincity.saas.entity.processor.enums.ConversionEventStatus;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorConversionEventsRecord;
import java.time.LocalDateTime;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets;
import reactor.core.publisher.Mono;

@Component
public class ConversionEventDAO extends BaseUpdatableDAO<EntityProcessorConversionEventsRecord, ConversionEvent> {

    protected ConversionEventDAO() {
        super(
                ConversionEvent.class,
                ENTITY_PROCESSOR_CONVERSION_EVENTS,
                ENTITY_PROCESSOR_CONVERSION_EVENTS.ID);
    }

    /**
     * Used by the stage-transition hook to honour the {@code (ticket_id, mapping_id)}
     * UNIQUE constraint without round-tripping through the DB's exception path.
     */
    public Mono<ConversionEvent> findByTicketAndMapping(ULong ticketId, ULong mappingId) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CONVERSION_EVENTS
                                .TICKET_ID
                                .eq(ticketId)
                                .and(ENTITY_PROCESSOR_CONVERSION_EVENTS.MAPPING_ID.eq(mappingId))))
                .map(e -> e.into(this.pojoClass));
    }

    /**
     * Cross-tenant pull of pending events the worker should dispatch on this run:
     * status PENDING or FAILED, and either {@code next_attempt_at IS NULL} or has elapsed.
     * Capped at {@code batchSize} to keep a single worker tick bounded.
     */
    public Flux<ConversionEvent> findDispatchable(int batchSize) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_CONVERSION_EVENTS
                                .STATUS
                                .in(ConversionEventStatus.PENDING, ConversionEventStatus.FAILED)
                                .and(ENTITY_PROCESSOR_CONVERSION_EVENTS
                                        .NEXT_ATTEMPT_AT
                                        .isNull()
                                        .or(ENTITY_PROCESSOR_CONVERSION_EVENTS.NEXT_ATTEMPT_AT.le(DSL.currentLocalDateTime()))))
                        .orderBy(ENTITY_PROCESSOR_CONVERSION_EVENTS.NEXT_ATTEMPT_AT.asc().nullsFirst(),
                                ENTITY_PROCESSOR_CONVERSION_EVENTS.ID.asc())
                        .limit(batchSize))
                .map(e -> e.into(this.pojoClass));
    }

    /**
     * Direct status update — avoids loading + re-saving the full record, since
     * the dispatcher hot-path runs per-event.
     */
    public Mono<Integer> markStatus(
            ULong id,
            ConversionEventStatus status,
            String statusMessage,
            LocalDateTime sentAt,
            LocalDateTime nextAttemptAt,
            int attemptCount) {

        return Mono.from(this.dslContext
                        .update(this.table)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.STATUS, status)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.STATUS_MESSAGE, statusMessage)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.SENT_AT, sentAt)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.NEXT_ATTEMPT_AT, nextAttemptAt)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.ATTEMPT_COUNT, attemptCount)
                        .where(ENTITY_PROCESSOR_CONVERSION_EVENTS.ID.eq(id)))
                .map(Integer::valueOf);
    }

    /**
     * Terminal state — explicitly clears {@code NEXT_ATTEMPT_AT} so the dispatcher's
     * {@code findDispatchable} ({@code IS NULL OR <= NOW()}) doesn't pick it up again.
     * Used for non-retryable conditions (ticket missing campaign attribution, dispatcher
     * not registered for platform, etc.).
     */
    /**
     * Moves SKIPPED events for a campaign back into the retry pool.
     *
     * <p>The counterpart to terminal classification. A missing pixel id stops the retries, but once
     * the id is set on the campaign those conversions are deliverable again, and nothing else would
     * ever pick them up: {@code findDispatchable} selects PENDING and FAILED only, by design.
     *
     * <p><b>ATTEMPT_COUNT is reset to zero, and that is load-bearing rather than tidy.</b> The rows
     * this exists for carry attempt counts in the hundreds or thousands — production had one at
     * 2,358 — so requeueing without clearing it would put them straight back over
     * {@code MAX_ATTEMPTS} and the very first failure would skip them again. The requeue would
     * appear to work and change nothing.
     *
     * <p>NEXT_ATTEMPT_AT is nulled so they are eligible on the next tick rather than inheriting a
     * backoff from a problem that has since been fixed.
     *
     * <p>Scoped by campaign through the ticket, because the events table has no campaign of its own.
     * A subquery rather than a join: it reads as the question being asked, and the ticket set per
     * campaign is small.
     */
    public Mono<Integer> requeueSkippedForCampaign(ULong campaignId, String statusMessage) {
        return Mono.from(this.dslContext
                        .update(this.table)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.STATUS, ConversionEventStatus.PENDING)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.STATUS_MESSAGE, statusMessage)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.ATTEMPT_COUNT, 0)
                        .setNull(ENTITY_PROCESSOR_CONVERSION_EVENTS.NEXT_ATTEMPT_AT)
                        .setNull(ENTITY_PROCESSOR_CONVERSION_EVENTS.SENT_AT)
                        .where(ENTITY_PROCESSOR_CONVERSION_EVENTS
                                .STATUS
                                .eq(ConversionEventStatus.SKIPPED)
                                .and(ENTITY_PROCESSOR_CONVERSION_EVENTS.TICKET_ID.in(this.dslContext
                                        .select(EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.ID)
                                        .from(EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS)
                                        .where(EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.CAMPAIGN_ID.eq(
                                                campaignId))))))
                .map(rows -> rows == null ? 0 : rows);
    }

    /** Counts what {@link #requeueSkippedForCampaign} would move, so a caller can check first. */
    public Mono<Integer> countSkippedForCampaign(ULong campaignId) {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(this.table)
                        .where(ENTITY_PROCESSOR_CONVERSION_EVENTS
                                .STATUS
                                .eq(ConversionEventStatus.SKIPPED)
                                .and(ENTITY_PROCESSOR_CONVERSION_EVENTS.TICKET_ID.in(this.dslContext
                                        .select(EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.ID)
                                        .from(EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS)
                                        .where(EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.CAMPAIGN_ID.eq(
                                                campaignId))))))
                .map(org.jooq.Record1::value1);
    }

    public Mono<Integer> markSkipped(ULong id, String statusMessage) {
        return Mono.from(this.dslContext
                        .update(this.table)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.STATUS, ConversionEventStatus.SKIPPED)
                        .set(ENTITY_PROCESSOR_CONVERSION_EVENTS.STATUS_MESSAGE, statusMessage)
                        .setNull(ENTITY_PROCESSOR_CONVERSION_EVENTS.NEXT_ATTEMPT_AT)
                        .where(ENTITY_PROCESSOR_CONVERSION_EVENTS.ID.eq(id)))
                .map(rows -> rows == null ? 0 : rows);
    }
}
