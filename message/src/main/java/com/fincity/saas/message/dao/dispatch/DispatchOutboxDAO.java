package com.fincity.saas.message.dao.message.provider.whatsapp;

import static com.fincity.saas.message.jooq.tables.MessageWhatsappOutbox.MESSAGE_WHATSAPP_OUTBOX;

import com.fincity.saas.message.dao.base.BaseProviderDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappOutbox;
import com.fincity.saas.message.enums.message.provider.whatsapp.WhatsappOutboxEventType;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappOutboxRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class WhatsappOutboxDAO extends BaseProviderDAO<MessageWhatsappOutboxRecord, WhatsappOutbox> {

    protected WhatsappOutboxDAO() {
        super(
                WhatsappOutbox.class,
                MESSAGE_WHATSAPP_OUTBOX,
                MESSAGE_WHATSAPP_OUTBOX.ID,
                MESSAGE_WHATSAPP_OUTBOX.META_MESSAGE_ID);
    }

    /**
     * Removes a handoff that the consumer accepted.
     *
     * <p>Deleted rather than flagged, so the table stays empty in the normal case and its size is a
     * usable health signal on its own.
     */
    public Mono<Integer> clear(String metaMessageId, WhatsappOutboxEventType eventType) {
        return Mono.from(this.dslContext
                .deleteFrom(MESSAGE_WHATSAPP_OUTBOX)
                .where(MESSAGE_WHATSAPP_OUTBOX.META_MESSAGE_ID.eq(metaMessageId))
                .and(MESSAGE_WHATSAPP_OUTBOX.EVENT_TYPE.eq(eventType)));
    }

    /**
     * Records a failed dispatch and pushes the next attempt out.
     *
     * <p>Backoff is computed in SQL off the stored count rather than read-modify-written, so two
     * sweeper instances racing the same row cannot reset each other's progress.
     */
    public Mono<Integer> recordFailure(ULong id, String error, LocalDateTime nextAttemptAt) {
        return Mono.from(this.dslContext
                .update(MESSAGE_WHATSAPP_OUTBOX)
                .set(MESSAGE_WHATSAPP_OUTBOX.ATTEMPTS, MESSAGE_WHATSAPP_OUTBOX.ATTEMPTS.plus(UInteger.valueOf(1)))
                .set(
                        MESSAGE_WHATSAPP_OUTBOX.LAST_ERROR,
                        error == null ? null : error.substring(0, Math.min(error.length(), 2000)))
                .set(MESSAGE_WHATSAPP_OUTBOX.NEXT_ATTEMPT_AT, nextAttemptAt)
                .where(MESSAGE_WHATSAPP_OUTBOX.ID.eq(id)));
    }

    /**
     * Handoffs due for a retry, oldest first.
     *
     * <p>{@code maxAttempts} is a hard stop: past it a row is left in place and no longer retried,
     * so a permanently undeliverable message stays visible for someone to look at instead of
     * spinning forever or being silently dropped.
     */
    public Mono<List<WhatsappOutbox>> readDue(LocalDateTime now, int maxAttempts, int limit) {
        return Flux.from(this.dslContext
                        .selectFrom(MESSAGE_WHATSAPP_OUTBOX)
                        .where(MESSAGE_WHATSAPP_OUTBOX.NEXT_ATTEMPT_AT
                                .isNull()
                                .or(MESSAGE_WHATSAPP_OUTBOX.NEXT_ATTEMPT_AT.le(now)))
                        .and(MESSAGE_WHATSAPP_OUTBOX.ATTEMPTS.lt(UInteger.valueOf(maxAttempts)))
                        .orderBy(MESSAGE_WHATSAPP_OUTBOX.CREATED_AT.asc())
                        .limit(limit))
                .map(rec -> rec.into(WhatsappOutbox.class))
                .collectList();
    }

    /** Rows that have exhausted their retries and need a human. Drives the alert, not a retry. */
    public Mono<Integer> countExhausted(int maxAttempts) {
        return Mono.from(this.dslContext
                        .selectCount()
                        .from(MESSAGE_WHATSAPP_OUTBOX)
                        .where(MESSAGE_WHATSAPP_OUTBOX.ATTEMPTS.ge(UInteger.valueOf(maxAttempts))))
                .map(rec -> rec.value1())
                .defaultIfEmpty(0);
    }
}
