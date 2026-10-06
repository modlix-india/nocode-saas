package com.fincity.saas.message.model.response.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * What the provider returns on accepting an outbound call, verified on a live dial: enough to record the call
 * outright. {@code requestId} is kept because the vendor's logs are searched by it.
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

    /** The number presented to the customer, which becomes the caller id. */
    @JsonProperty("VirtualNumber")
    private String virtualNumber;

    /** Where the call originated — for a browser call, the agent's SIP endpoint. */
    @JsonProperty("FromNumber")
    private String fromNumber;

    /** "active" once the invite has gone out. */
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
