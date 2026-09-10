package com.fincity.saas.message.model.response.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * What the provider tells us immediately after accepting an outbound call.
 *
 * <p>More than expected, and enough to record the call outright: verified against a live dial, the
 * response carries the {@code CallSid}, the {@code VirtualNumber} presented to the customer, the
 * {@code FromNumber} the call originated from, and a {@code CallState} of {@code "active"} once the
 * invite has been dispatched. Nothing has to be correlated afterwards.
 *
 * <p>{@code requestId} is kept even though the Sid supersedes it: it is the identifier the vendor's
 * own logs are searched by, and the only thing to quote about a call that never materialised.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Accessors(chain = true)
public class ExotelOutboundCallResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 8021554317690288412L;

    private String requestId;

    @JsonProperty("CallSid")
    private String callSid;

    /** The number the provider actually presented to the customer, which becomes the caller id. */
    @JsonProperty("VirtualNumber")
    private String virtualNumber;

    /** Where the call originated — for a browser call, the agent's SIP endpoint. */
    @JsonProperty("FromNumber")
    private String fromNumber;

    /** The provider's own view of the call at dispatch: "active" once the invite has gone out. */
    @JsonProperty("CallState")
    private String callState;

    public static ExotelOutboundCallResult of(
            String requestId, String callSid, String virtualNumber, String fromNumber, String callState) {

        return new ExotelOutboundCallResult()
                .setRequestId(requestId)
                .setCallSid(callSid)
                .setVirtualNumber(virtualNumber)
                .setFromNumber(fromNumber)
                .setCallState(callState);
    }
}
