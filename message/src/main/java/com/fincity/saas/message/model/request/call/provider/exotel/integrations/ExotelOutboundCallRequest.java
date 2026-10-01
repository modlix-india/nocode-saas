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
 * <p>Exactly the fields the vendor's SDK sends; there is no caller-ID field, so the CLI cannot be chosen per
 * call. {@code user_id} is the agent's {@code AppUserId}, taken from the stored endpoint row, never a request.
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
