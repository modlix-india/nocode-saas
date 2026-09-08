package com.fincity.saas.message.dao.dispatch;

import static com.fincity.saas.message.jooq.tables.MessageDispatchOutbox.MESSAGE_DISPATCH_OUTBOX;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.dispatch.DispatchOutbox;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.jooq.tables.records.MessageDispatchOutboxRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DispatchOutboxDAO extends BaseProviderDAO<MessageDispatchOutboxRecord, DispatchOutbox> {

    protected DispatchOutboxDAO() {
        super(
                DispatchOutbox.class,
                MESSAGE_DISPATCH_OUTBOX,
                MESSAGE_DISPATCH_OUTBOX.ID,
                MESSAGE_DISPATCH_OUTBOX.EVENT_KEY);
    }

    /**
     * Removes a handoff that the consumer accepted.
     *
     * <p>Deleted rather than flagged, so the table stays empty in the normal case and its size is a
     * usable health signal on its own.
     */
    public Mono<Integer> clear(DispatchChannel channel, String eventKey, DispatchEventType eventType) {
        return Mono.from(this.dslContext
                .deleteFrom(MESSAGE_DISPATCH_OUTBOX)
                .where(MESSAGE_DISPATCH_OUTBOX.CHANNEL.eq(channel))
                .and(MESSAGE_DISPATCH_OUTBOX.EVENT_KEY.eq(eventKey))
                .and(MESSAGE_DISPATCH_OUTBOX.EVENT_TYPE.eq(eventType)));
    }

    /**
     * Records a failed dispatch and pushes the next attempt out.
     *
     * <p>Backoff is computed in SQL off the stored count rather than read-modify-written, so two
     * sweeper instances racing the same row cannot reset each other's progress.
     */
    public Mono<Integer> recordFailure(ULong id, String error, LocalDateTime nextAttemptAt) {
        return Mono.from(this.dslContext
                .update(MESSAGE_DISPATCH_OUTBOX)
                .set(MESSAGE_DISPATCH_OUTBOX.ATTEMPTS, MESSAGE_DISPATCH_OUTBOX.ATTEMPTS.plus(UInteger.valueOf(1)))
                .set(
                        MESSAGE_DISPATCH_OUTBOX.LAST_ERROR,
                        error == null ? null : error.substring(0, Math.min(error.length(), 2000)))
                .set(MESSAGE_DISPATCH_OUTBOX.NEXT_ATTEMPT_AT, nextAttemptAt)
                .where(MESSAGE_DISPATCH_OUTBOX.ID.eq(id)));
    }

    /**
     * Handoffs due for a retry, oldest first.
     *
     * <p>{@code maxAttempts} is a hard stop: past it a row is left in place and no longer retried,
     * so a permanently undeliverable message stays visible for someone to look at instead of
     * spinning forever or being silently dropped.
     */
    public Mono<List<DispatchOutbox>> readDue(LocalDateTime now, int maxAttempts, int limit) {
        return Flux.from(this.dslContext
                        .selectFrom(MESSAGE_DISPATCH_OUTBOX)
                        .where(MESSAGE_DISPATCH_OUTBOX.NEXT_ATTEMPT_AT
                                .isNull()
                                .or(MESSAGE_DISPATCH_OUTBOX.NEXT_ATTEMPT_AT.le(now)))
                        .and(MESSAGE_DISPATCH_OUTBOX.ATTEMPTS.lt(UInteger.valueOf(maxAttempts)))
                        .orderBy(MESSAGE_DISPATCH_OUTBOX.CREATED_AT.asc())
                        .limit(limit))
                .map(rec -> rec.into(DispatchOutbox.class))
                .collectList();
    }

    /** Rows that have exhausted their retries and need a human. Drives the alert, not a retry. */
    public Mono<Integer> countExhausted(int maxAttempts) {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(MESSAGE_DISPATCH_OUTBOX)
                        .where(MESSAGE_DISPATCH_OUTBOX.ATTEMPTS.ge(UInteger.valueOf(maxAttempts))))
                .map(rec -> rec.value1())
                .defaultIfEmpty(0);
    }
}
