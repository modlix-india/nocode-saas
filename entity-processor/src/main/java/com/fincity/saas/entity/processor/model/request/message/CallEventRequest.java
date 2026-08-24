package com.fincity.saas.entity.processor.model.request.message;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A call event handed over by the message service.
 *
 * <p>A cross-service contract, and the call-side twin of {@link WhatsappInboundRequest}: the message
 * service owns the provider relationship, this service owns what the call means, so the payload is
 * provider-shaped and carries no notion of a deal.
 *
 * <p>Almost every event is a status update for a call this service already recorded, because both
 * directions are initiated here: an outbound call is placed through the gated endpoint and an inbound
 * one goes through the connect applet, and both write their row at the moment they happen. The
 * exception is a call placed some other way, which arrives here with no matching row and is created
 * from whatever the event carries.
 *
 * <p>Changing a field here changes both services. Add rather than repurpose, and treat {@code
 * providerCallId} as immutable: it is the idempotency key that makes redelivery, outbox replay and
 * out-of-order status updates safe.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CallEventRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = -6019338841205274419L;

    /** The provider's call id (Exotel's Sid). The idempotency key: every write path upserts on it. */
    private String providerCallId;

    private String parentCallSid;
    private String accountSid;

    /** {@code CALL_STATUS} today. Present so a second event kind can be added without a guess. */
    private String eventType;

    private String callProvider;
    private String connectionName;

    /**
     * Product the business number is mapped to, or null when that number is the tenant default and
     * therefore serves every product.
     */
    private BigInteger productId;

    private Boolean outbound;

    private Integer fromDialCode;
    private String from;
    private Integer toDialCode;
    private String to;
    private Integer customerDialCode;
    private String customerPhoneNumber;
    private String callerId;

    /** The provider's own status string, as sent, e.g. {@code in-progress}. */
    private String callStatus;

    private String leg1Status;
    private String leg2Status;
    private String direction;
    private String answeredBy;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private Long conversationDuration;
    private String price;
    private String recordingUrl;

    /** Raw provider payloads, stored verbatim and never interpreted on this side. */
    private Map<String, Object> exotelCallRequest;

    private Map<String, Object> exotelConnectAppletRequest;
    private Map<String, Object> exotelCallResponse;
}
