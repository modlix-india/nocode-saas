package com.fincity.saas.message.model.response.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A registered integration app.
 *
 * <p>{@code AppSecret} comes back <b>only from {@code POST /app}</b>, at creation, and never
 * again: the {@code GET /app} listing omits it, the way most platforms show a secret once. It has
 * to be captured from the creation response and persisted there and then, because there is no way
 * to read it back afterwards.
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelAppData implements Serializable {

    @Serial
    private static final long serialVersionUID = 5540311982246137690L;

    @JsonProperty("AppID")
    private String appId;

    /**
     * Present on the creation response only. Null on every listing.
     *
     * <p>Capture it when it appears or the app becomes unusable: nothing else can mint the app token
     * that user mapping and browser sessions both depend on.
     */
    @JsonProperty("AppSecret")
    @ToString.Exclude
    private String appSecret;

    @JsonProperty("CustomerID")
    private String customerId;

    @JsonProperty("AppName")
    private String appName;

    @JsonProperty("ExotelAccountSid")
    private String exotelAccountSid;

    @JsonProperty("ExotelDomain")
    private String exotelDomain;

    @JsonProperty("IsActive")
    private Boolean isActive;
}
