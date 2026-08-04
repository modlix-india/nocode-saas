package com.fincity.saas.entity.processor.model.request.message;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A WhatsApp event handed over by the message service.
 *
 * <p>This is a cross-service contract. The message service owns the provider relationship and this
 * service owns what the conversation means, so the payload is deliberately provider-shaped and free
 * of any notion of a deal: the sender cannot know which deal a message belongs to, and the receiver
 * does not need to know how Meta framed it.
 *
 * <p>Changing a field here changes both services. Add rather than repurpose, and treat {@code
 * metaMessageId} as immutable, since it is the idempotency key that makes redelivery, outbox replay
 * and out-of-order status updates all safe.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class WhatsappInboundRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3315510394855230071L;

    /** Meta's message id. The idempotency key: every write path upserts on it. */
    private String metaMessageId;

    /** {@code INBOUND_MESSAGE} or {@code MESSAGE_STATUS}. */
    private String eventType;

    /**
     * Product the receiving business number is mapped to, or null when that number is the tenant
     * default and therefore serves every product. Null means match on the customer's number alone.
     */
    private java.math.BigInteger productId;

    private java.math.BigInteger whatsappPhoneNumberId;
    private java.math.BigInteger whatsappBusinessAccountId;

    /** The business number as dialled, stored so history survives a later re-mapping. */
    private String whatsappPhoneNumber;

    private String customerWaId;
    private Integer customerDialCode;
    private String customerPhoneNumber;

    private String from;
    private String to;

    private String messageType;
    private String messageStatus;

    /** Meta's own timestamp, not our receive time, so a replayed event keeps its real ordering. */
    private LocalDateTime occurredAt;

    /** Plain text of the message, extracted by the sender so the receiver never parses a payload. */
    private String bodyText;

    private Boolean outbound;
    private String failureReason;

    /** Raw provider payloads, stored verbatim and never interpreted on this side. */
    private Map<String, Object> message;

    private Map<String, Object> inMessage;
    private Map<String, Object> messageResponse;
    private Map<String, Object> mediaFileDetail;

    public boolean isStatusUpdate() {
        return "MESSAGE_STATUS".equals(this.eventType);
    }
}
