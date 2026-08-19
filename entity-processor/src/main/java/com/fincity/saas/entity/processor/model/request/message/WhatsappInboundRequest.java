package com.fincity.saas.entity.processor.model.request.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * The linked session that carried the message.
     *
     * <p>Kept alongside the dialled number rather than derived from it, because a number can be
     * unlinked and relinked and the pacing questions are asked of the session.
     */
    private String bridgeSessionId;

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

    private String mediaMimeType;
    private String mediaFileName;
    private Long mediaSize;
    private Integer mediaDurationSeconds;
    private Boolean mediaIsVoiceNote;

    /**
     * Why an attachment will never arrive - too large, or out of retries.
     *
     * <p>Carried so the bubble can say so. An attachment that failed and one that is still on its
     * way look identical from the row alone, and a thread showing an empty frame forever is worse
     * than one that admits the file is not coming.
     */
    private String mediaError;

    private String reactionToMessageId;

    /**
     * Whether this handoff carries only an attachment for a message already stored.
     *
     * <p>Matched on the string rather than an enum for the same reason the type and status are:
     * this is a wire contract with a service that is not Java, and a value it sends which we do not
     * recognise has to degrade rather than fail to deserialise.
     */
    @JsonIgnore
    public boolean isMediaReady() {
        return "MEDIA_READY".equalsIgnoreCase(this.eventType);
    }

    public boolean isStatusUpdate() {
        return "MESSAGE_STATUS".equals(this.eventType);
    }
}
