package com.fincity.saas.message.model.request.dispatch;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A call event handed to the service that owns the call.
 *
 * <p>A cross-service contract, and the call-side twin of {@code WhatsappInboundDispatch}: this
 * service owns the provider relationship, the consumer owns what the call means, so the payload is
 * provider-shaped and carries no notion of a deal.
 *
 * <p>Changing a field here changes both services. Add rather than repurpose, and treat {@code
 * providerCallId} as immutable: it is the idempotency key that makes redelivery, outbox replay and
 * out-of-order status updates all safe.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CallEventDispatch implements Serializable {

    @Serial
    private static final long serialVersionUID = 4413364176830218395L;

    /** The provider's call id (Exotel's Sid). The idempotency key: the consumer upserts on it. */
    private String providerCallId;

    private String parentCallSid;
    private String accountSid;

    /** {@code CALL_STATUS} today. Present so a second event kind can be added without a guess. */
    private String eventType;

    private String callProvider;
    private String connectionName;
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

    /** Raw provider payloads, passed through so the consumer never re-parses a callback. */
    private Map<String, Object> exotelCallRequest;

    private Map<String, Object> exotelConnectAppletRequest;
    private Map<String, Object> exotelCallResponse;
}
