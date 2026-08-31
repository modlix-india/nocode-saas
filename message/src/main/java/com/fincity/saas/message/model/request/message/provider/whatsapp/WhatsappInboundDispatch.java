package com.fincity.saas.message.model.request.message.provider.whatsapp;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What this service hands to the service that owns the receiving WhatsApp number.
 *
 * <p>Cross-service contract, and the sending half of entity-processor's {@code
 * WhatsappInboundRequest}. Keep the two in step: a field renamed on one side silently arrives null
 * on the other, which for {@code metaMessageId} would break idempotency and start duplicating
 * messages rather than failing visibly.
 *
 * <p>Deliberately carries no notion of a deal. This service cannot know which deal a message
 * belongs to, and the owner does not need to know how Meta framed it. {@code bodyText} is extracted
 * here so the owner never has to parse a provider payload.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class WhatsappInboundDispatch implements Serializable {

    @Serial
    private static final long serialVersionUID = 6134809972245510033L;

    private String metaMessageId;
    private String eventType;

    /** Null when the receiving number is the tenant default, which serves every product. */
    private BigInteger productId;

    private BigInteger whatsappPhoneNumberId;
    private BigInteger whatsappBusinessAccountId;
    private String whatsappPhoneNumber;

    private String customerWaId;
    private Integer customerDialCode;
    private String customerPhoneNumber;

    private String from;
    private String to;

    private String messageType;
    private String messageStatus;

    /** Meta's timestamp, not our receive time, so a replay keeps its real position in the thread. */
    private LocalDateTime occurredAt;

    private String bodyText;
    private Boolean outbound;
    private String failureReason;

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
     * Where the inline preview was stored, once uploaded. Not the base64 the bridge sent: that is
     * turned into a file on this hop, so the row downstream carries a URL rather than bytes.
     */
    private Map<String, Object> mediaThumbnailFileDetail;

    private Integer mediaPageCount;
    private String mediaError;
    private String reactionToMessageId;

    /**
     * Where the customer's avatar was stored, on a PROFILE_PICTURE dispatch. Null means they removed
     * it and whatever is held should be cleared.
     */
    private Map<String, Object> profilePictureFileDetail;

    /** WhatsApp's id for that image, so the next fetch can be answered with "unchanged". */
    private String profilePictureId;

    /**
     * Which linked number this belongs to.
     *
     * <p>Missing until now, and the omission was not cosmetic. The consumer stores it on every row
     * and its pacing queries window on it, so with inbound rows carrying null the reply-rate count
     * matched nothing and was structurally zero - the signal that decides whether a number is
     * healthy enough to keep sending was reading empty for one whole direction.
     */
    private String bridgeSessionId;

    /**
     * The name the customer has set on their own WhatsApp profile, as WhatsApp sends it with every
     * inbound message.
     *
     * <p>Carried because it is the only thing in an inbound message that names the person. Without
     * it, a deal created for a stranger who messages in can only be called after their phone number,
     * which is what every such deal was called. The bridge has always sent it; this hop simply never
     * copied it across.
     *
     * <p>Untrusted display text, set by whoever is messaging. It names a deal and is never matched
     * on, so the consumer bounds its length and treats a blank as absent.
     */
    private String pushName;

    /**
     * Whether the number this arrived on is the tenant's default.
     *
     * <p>Only this service can answer it, because the flag lives on the session row here. The
     * consumer needs it to decide which products a message on this number may belong to: a product
     * that names no number sends through the default, so a message on the default legitimately
     * belongs to a deal on a product that does not name it.
     */
    private Boolean sessionIsDefault;

    /**
     * Whether this message was recovered from WhatsApp's history sync rather than received live.
     *
     * <p>Carried so the consumer can refuse to create deals for it. See
     * {@code BridgeEvent.backfilled}.
     */
    private Boolean backfilled;

    /**
     * Tappable actions the sender attached: a "Pay Now" URL, a call button, a quick reply.
     *
     * <p>Flat maps rather than a typed model on purpose. This service only forwards them, and the
     * page that draws them reads fields off each entry directly, so a shared shape here would buy
     * nothing and cost a class in every service on the path.
     */
    private List<Map<String, Object>> buttons;
}
