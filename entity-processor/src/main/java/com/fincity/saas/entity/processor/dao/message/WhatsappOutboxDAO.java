package com.fincity.saas.entity.processor.dao.message;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorWhatsappOutbox.ENTITY_PROCESSOR_WHATSAPP_OUTBOX;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.message.WhatsappOutbox;
import com.fincity.saas.entity.processor.enums.message.WhatsappOutboxStatus;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorWhatsappOutboxRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class WhatsappOutboxDAO extends BaseUpdatableDAO<EntityProcessorWhatsappOutboxRecord, WhatsappOutbox> {

    protected WhatsappOutboxDAO() {
        super(WhatsappOutbox.class, ENTITY_PROCESSOR_WHATSAPP_OUTBOX, ENTITY_PROCESSOR_WHATSAPP_OUTBOX.ID);
    }

    /**
     * Messages the sweeper should look at.
     *
     * <p>Deliberately not tenant-scoped: the sweeper is a background task with no user context and
     * drains every tenant. Each row carries its own app and client code and the send is made under
     * those, so widening here does not widen anything downstream.
     *
     * <p>Ordered by ticket then sequence, so a packet is considered in the order it was queued and
     * the "an earlier one failed" check is looking at messages that really did come first.
     */
    public Mono<List<WhatsappOutbox>> readDue(LocalDateTime now, int limit) {
        return Flux.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                        .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS.eq(WhatsappOutboxStatus.PENDING))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX
                                .EARLIEST_SEND_AT
                                .isNull()
                                .or(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.EARLIEST_SEND_AT.le(now)))
                        .and(this.isActiveTrue())
                        .orderBy(
                                ENTITY_PROCESSOR_WHATSAPP_OUTBOX.TICKET_ID.asc(),
                                ENTITY_PROCESSOR_WHATSAPP_OUTBOX.SEQUENCE_ORDER.asc(),
                                ENTITY_PROCESSOR_WHATSAPP_OUTBOX.ID.asc())
                        .limit(limit))
                .map(rec -> rec.into(WhatsappOutbox.class))
                .collectList();
    }

    /**
     * Records that a gate refused to release a row.
     *
     * <p>The reason is always written, never left null. A pending row with no reason is exactly what
     * makes this design unexplainable when somebody asks in three months why a lead never got their
     * brochure.
     */
    public Mono<Integer> hold(ULong id, String reason, LocalDateTime earliestSendAt) {
        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.HOLD_REASON, reason)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.EARLIEST_SEND_AT, earliestSendAt)
                .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.ID.eq(id)));
    }

    public Mono<Integer> markSent(ULong id, String messageId, String decision, ULong forcedBy, LocalDateTime sentAt) {
        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS, WhatsappOutboxStatus.SENT)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.MESSAGE_ID, messageId)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.SEND_DECISION, decision)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.FORCED_BY, forcedBy)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.SENT_AT, sentAt)
                .setNull(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.HOLD_REASON)
                .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.ID.eq(id)));
    }

    /** Records a failed attempt, and gives up once the ceiling is reached. */
    public Mono<Integer> recordFailure(ULong id, String error, int attempts, int maxAttempts) {
        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                .set(
                        ENTITY_PROCESSOR_WHATSAPP_OUTBOX.ATTEMPTS,
                        ENTITY_PROCESSOR_WHATSAPP_OUTBOX.ATTEMPTS.plus(UInteger.valueOf(1)))
                .set(
                        ENTITY_PROCESSOR_WHATSAPP_OUTBOX.LAST_ERROR,
                        error == null ? null : error.substring(0, Math.min(error.length(), 2000)))
                .set(
                        ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS,
                        attempts + 1 >= maxAttempts ? WhatsappOutboxStatus.FAILED : WhatsappOutboxStatus.PENDING)
                .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.ID.eq(id)));
    }

    /**
     * Stops the rest of a deal's queued sequence.
     *
     * <p>Used when a lead opts out, goes quiet, or an earlier message in the packet failed. Cancelled
     * rather than deleted, because "we decided not to send this, and here is why" is information
     * somebody will want later, and a missing row cannot say it.
     */
    public Mono<Integer> cancelPendingForTicket(ULong ticketId, String reason) {
        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS, WhatsappOutboxStatus.CANCELLED)
                .set(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.HOLD_REASON, reason)
                .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.TICKET_ID.eq(ticketId))
                .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS.eq(WhatsappOutboxStatus.PENDING)));
    }

    /** Whether an earlier message in this deal's packet failed, which stops the rest. */
    public Mono<Boolean> hasEarlierFailure(ULong ticketId, int sequenceOrder) {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                        .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.TICKET_ID.eq(ticketId))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.SEQUENCE_ORDER.lt(UInteger.valueOf(sequenceOrder)))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS.eq(WhatsappOutboxStatus.FAILED)))
                .map(rec -> rec.value1() > 0)
                .defaultIfEmpty(Boolean.FALSE);
    }

    /** The queue for one deal, for the UI and for the override panel. */
    public Mono<List<WhatsappOutbox>> readForTicket(String appCode, String clientCode, ULong ticketId) {
        return Flux.from(this.dslContext
                        .selectFrom(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                        .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.APP_CODE.eq(appCode))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.CLIENT_CODE.eq(clientCode))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.TICKET_ID.eq(ticketId))
                        .orderBy(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.SEQUENCE_ORDER.asc()))
                .map(rec -> rec.into(WhatsappOutbox.class))
                .collectList();
    }

    /** How many messages this rule already queued for this deal, so a stage bounce cannot re-enrol it. */
    public Mono<Integer> countForTicketAndConfig(ULong ticketId, ULong configId) {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                        .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.TICKET_ID.eq(ticketId))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.CONFIG_ID.eq(configId)))
                .map(Record1::value1)
                .defaultIfEmpty(0);
    }

    /** Rows stuck pending far longer than they should be, for the alert. */
    public Mono<Integer> countStale(LocalDateTime before) {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                        .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS.eq(WhatsappOutboxStatus.PENDING))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.CREATED_AT.lt(before)))
                .map(Record1::value1)
                .defaultIfEmpty(0);
    }

    /** Sends made from a session today, counted from this table rather than the message table. */
    public Mono<Integer> sentFromSessionSince(String sessionId, LocalDateTime since) {
        if (sessionId == null || sessionId.isBlank()) return Mono.just(0);

        return Mono.from(this.dslContext
                        .selectCount()
                        .from(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                        .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.BRIDGE_SESSION_ID.eq(sessionId))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS.eq(WhatsappOutboxStatus.SENT))
                        .and(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.SENT_AT.ge(since)))
                .map(Record1::value1)
                .defaultIfEmpty(0);
    }

    /** Total queued and held right now, grouped for the metrics endpoint. */
    public Mono<Integer> countPending() {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(ENTITY_PROCESSOR_WHATSAPP_OUTBOX)
                        .where(ENTITY_PROCESSOR_WHATSAPP_OUTBOX.STATUS.eq(WhatsappOutboxStatus.PENDING))
                        .and(DSL.trueCondition()))
                .map(Record1::value1)
                .defaultIfEmpty(0);
    }
}
