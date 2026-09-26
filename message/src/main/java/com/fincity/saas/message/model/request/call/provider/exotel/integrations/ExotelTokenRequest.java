package com.fincity.saas.message.model.request.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A token request to Exotel's Integrations Core.
 *
 * <p>One endpoint, two body shapes, which is why the fields are nullable and unset ones are omitted:
 * customer and app tokens are asked for with {@code Id}/{@code Secret}/{@code Entity}, while an
 * agent session token uses {@code AppId}/{@code AppSecret}/{@code AppUserId}. Note the casing
 * differs between them — {@code Id} versus {@code AppId} — which is Exotel's, not a typo here.
 *
 * <p>This endpoint takes no {@code Authorization} header. It is what issues them.
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

    @JsonProperty("AppId")
    private String appId;

    @JsonProperty("AppSecret")
    @ToString.Exclude
    private String appSecret;

    @JsonProperty("AppUserId")
    private String appUserId;

    /** Customer-scoped token: authenticates the organisation to manage its apps. */
    public static ExotelTokenRequest ofCustomer(String customerId, String customerSecret) {
        return new ExotelTokenRequest()
                .setId(customerId)
                .setSecret(customerSecret)
                .setEntity("customer");
    }

    /**
     * App-scoped token.
     *
     * <p>Required, not interchangeable with the customer token: user mappings and app settings bind
     * to the account this app belongs to only when the app token is used. With the customer token
     * they can fall back to a different tenant entirely.
     */
    public static ExotelTokenRequest ofApp(String appId, String appSecret) {
        return new ExotelTokenRequest().setId(appId).setSecret(appSecret).setEntity("app");
    }

    /** Agent session token: what a browser softphone registers with. */
    public static ExotelTokenRequest ofAgent(String appId, String appSecret, String appUserId) {
        return new ExotelTokenRequest()
                .setId(appId)
                .setSecret(appSecret)
                .setEntity("app_user")
                .setAppId(appId)
                .setAppSecret(appSecret)
                .setAppUserId(appUserId);
    }
}
