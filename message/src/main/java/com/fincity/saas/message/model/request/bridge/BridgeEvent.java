package com.fincity.saas.message.model.request.bridge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * One inbound message or receipt, drained from a bridge's local outbox.
 *
 * <p>Receiving half of the Go {@code outbox.Event}. The bridge deletes its copy the moment this
 * service answers 2xx, and WhatsApp will not redeliver, so the response must not be sent until the
 * dispatch outbox row is durable. Acknowledging on receipt rather than on commit loses the message
 * outright.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BridgeEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 3357890014162268449L;

    /** The session id this service assigned, which is the phone number row's code. */
    private String sessionId;

    private DispatchEventType eventType;

    /**
     * WhatsApp's own id for the message, and the idempotency key for the entire chain.
     *
     * <p>Every write path from here to {@code entity_processor_whatsapp_messages} upserts on it,
     * which is what makes redelivery, outbox replay and an out-of-order status update all safe
     * rather than duplicating a message or losing one.
     */
    private String messageId;

    /**
     * Lowercase, deliberately, and this is load-bearing.
     *
     * <p>entity-processor's {@code WhatsappMessageType} and {@code WhatsappMessageStatus} serialise
     * lowercase via {@code @JsonValue}, and roughly 150 dealProfile expressions compare against
     * those exact strings. Uppercase here blanks every chat bubble in the UI while every service in
     * the chain returns 200. That has happened once already; these two stay strings and are passed
     * through untouched rather than being round-tripped through a Java enum that would re-case them.
     */
    private String messageType;

    private String messageStatus;

    private String customerWaId;
    private String customerPhoneNumber;
    private String businessPhoneNumber;

    private String from;
    private String to;

    /** Distinguishes a message we sent from one the customer sent. Receipts for ours are outbound. */
    private Boolean outbound;

    /** Extracted by the bridge, so nothing downstream ever parses a provider payload. */
    private String bodyText;

    /**
     * WhatsApp's timestamp, not our receive time, so a replayed event keeps its real position in the
     * thread. Instant because Go sends RFC 3339 with an offset.
     */
    private Instant occurredAt;

    private String pushName;

    /**
     * Where the bridge stored an attachment. Present only on MEDIA_READY, which is the second half
     * of a media message: the first half already arrived under this same message id.
     */
    private Map<String, Object> mediaFileDetail;

    /** As WhatsApp reported it, not guessed from the extension. Decides which player the UI mounts. */
    private String mediaMimeType;

    private String mediaFileName;
    private Long mediaSize;
    private Integer mediaDurationSeconds;

    /**
     * A recorded voice note rather than an attached audio file. Both arrive as AudioMessage and only
     * this flag separates them, and they render as completely different things.
     */
    private Boolean mediaIsVoiceNote;

    /**
     * Why an attachment will never arrive - too large, or out of retries. Set instead of the file
     * details, so a bubble can say so rather than showing an empty frame indefinitely.
     */
    private String mediaError;

    /**
     * The message a reaction applies to. Without it a reaction is an orphan: the emoji arrives under
     * the reaction's own id with nothing saying what it was aimed at.
     */
    private String reactionToMessageId;
}
