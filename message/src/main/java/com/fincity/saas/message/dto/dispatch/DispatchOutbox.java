package com.fincity.saas.message.dto.dispatch;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * One pending handoff from this service to the service that owns the event.
 *
 * <p>Rows are committed before dispatch and deleted on success, so the table is normally empty and
 * a non-empty one is the alert. There is no processed flag on purpose: {@code
 * message_message_webhooks.IS_PROCESSED} is the cautionary example, sitting at tens of thousands
 * unprocessed against a hundred processed because it is only set when an entire event succeeds.
 *
 * <p>The 200 back to Meta is returned once one of these is durable, not once the consumer has
 * accepted it, so a consumer outage never makes us look unavailable to Meta.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class DispatchOutbox extends BaseUpdatableDto<DispatchOutbox> {

    /** Eureka service id to dispatch to, taken from the receiving phone number's owner. */
    private String ownerService;

    /** Which family of provider events this is, and part of the idempotency key. */
    private DispatchChannel channel;

    /**
     * The provider's own id for the thing: a Meta message id, or an Exotel call Sid. The idempotency
     * key for the whole handoff. The consumer upserts on it, which is what makes replay after a
     * failed delete harmless and makes out-of-order arrival a non-issue.
     */
    private String eventKey;

    private DispatchEventType eventType;

    /** The complete dispatch body, so a replay never has to re-parse the original webhook. */
    private Map<String, Object> payload;

    private Integer attempts = 0;
    private String lastError;

    /** Earliest next retry, set by the backoff. Null means eligible now. */
    private LocalDateTime nextAttemptAt;

    public DispatchOutbox() {
        super();
    }

    public DispatchOutbox(DispatchOutbox outbox) {
        super(outbox);
        this.ownerService = outbox.ownerService;
        this.channel = outbox.channel;
        this.eventKey = outbox.eventKey;
        this.eventType = outbox.eventType;
        this.payload = outbox.payload;
        this.attempts = outbox.attempts;
        this.lastError = outbox.lastError;
        this.nextAttemptAt = outbox.nextAttemptAt;
    }

    public static DispatchOutbox of(
            String ownerService, String eventKey, DispatchEventType eventType, Map<String, Object> payload) {
        return new DispatchOutbox()
                .setOwnerService(ownerService)
                .setChannel(eventType.getChannel())
                .setEventKey(eventKey)
                .setEventType(eventType)
                .setPayload(payload);
    }
}
