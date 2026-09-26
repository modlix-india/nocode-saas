package com.fincity.saas.message.model.request.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Places an outbound call for a browser-registered agent, sent with that agent's own token.
 *
 * <p>The four required fields are exactly what the vendor's own SDK sends — read from
 * {@code MakeCall} in {@code ExotelWebPhoneSDK.ts}, which posts
 * {@code {customer_id, app_id, to, user_id}} and nothing else. Notably absent: any caller-ID or
 * virtual-number field, so the CLI cannot be chosen per call. A tenant needing different numbers
 * per product needs a separate provider app for each, not a different payload.
 *
 * <p>{@code user_id} is the agent's {@code AppUserId} — their email address — and it is what makes
 * the right agent's browser ring rather than someone else's. It is taken from the stored endpoint
 * row, never from a request.
 *
 * <p>Nothing else is sent. A custom field was carried here for a while, to tie the call back to the
 * row that had been written before it — until a live dial showed the response returning the
 * {@code CallSid} synchronously, which makes the row recordable outright and the correlation
 * unnecessary.
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExotelOutboundCallRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3488157421905566731L;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("app_id")
    private String appId;

    @JsonProperty("to")
    private String to;

    @JsonProperty("user_id")
    private String userId;

    public static ExotelOutboundCallRequest of(String customerId, String appId, String to, String userId) {

        return new ExotelOutboundCallRequest()
                .setCustomerId(customerId)
                .setAppId(appId)
                .setTo(to)
                .setUserId(userId);
    }
}
