package com.fincity.saas.message.model.request.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Registers an integration app against an Exotel account. Sent with the customer token.
 *
 * <p>Carries the telephony credentials too. Exotel binds the app to the account by way of them, so
 * an app created without them registers but cannot place calls — which fails later, and somewhere
 * unrelated. Shape taken from the vendor's own Postman collection.
 */
@Data
@Accessors(chain = true)
public class ExotelAppRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1359265102938471077L;

    @JsonProperty("AppName")
    private String appName;

    @JsonProperty("ExotelAccountSid")
    private String exotelAccountSid;

    @JsonProperty("ExotelApiKey")
    @ToString.Exclude
    private String exotelApiKey;

    @JsonProperty("ExotelApiToken")
    @ToString.Exclude
    private String exotelApiToken;

    /** The account's region, as Exotel names it — "Mumbai" for the in1 / mum1 hosts. */
    @JsonProperty("ExotelDomain")
    private String exotelDomain;

    @JsonProperty("IsActive")
    private Boolean isActive;

    public static ExotelAppRequest of(
            String appName, String exotelAccountSid, String apiKey, String apiToken, String domain) {
        return new ExotelAppRequest()
                .setAppName(appName)
                .setExotelAccountSid(exotelAccountSid)
                .setExotelApiKey(apiKey)
                .setExotelApiToken(apiToken)
                .setExotelDomain(domain)
                .setIsActive(Boolean.TRUE);
    }
}
