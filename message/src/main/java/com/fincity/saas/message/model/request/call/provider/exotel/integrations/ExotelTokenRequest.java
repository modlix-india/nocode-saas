package com.fincity.saas.message.model.request.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A token request to Exotel's Integrations Core, in Exotel's casing. {@code Entity} is only {@code customer} or
 * {@code app}: Exotel has no agent-scoped token. This endpoint takes no {@code Authorization} header.
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExotelTokenRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 4471039288104150122L;

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Secret")
    @ToString.Exclude
    private String secret;

    @JsonProperty("Entity")
    private String entity;

    /** Customer-scoped token: authenticates the organisation to manage its apps. */
    public static ExotelTokenRequest ofCustomer(String customerId, String customerSecret) {
        return new ExotelTokenRequest()
                .setId(customerId)
                .setSecret(customerSecret)
                .setEntity("customer");
    }

    /** App-scoped token, required for user mappings and app settings to bind to the right account. */
    public static ExotelTokenRequest ofApp(String appId, String appSecret) {
        return new ExotelTokenRequest().setId(appId).setSecret(appSecret).setEntity("app");
    }
}
