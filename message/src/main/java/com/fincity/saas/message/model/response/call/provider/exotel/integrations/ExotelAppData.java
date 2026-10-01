package com.fincity.saas.message.model.response.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A registered integration app. {@code AppSecret} comes back only from {@code POST /app}, at creation, and
 * must be persisted then: the {@code GET /app} listing omits it.
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelAppData implements Serializable {

    @Serial
    private static final long serialVersionUID = 5540311982246137690L;

    @JsonProperty("AppID")
    private String appId;

    /** Present on the creation response only; nothing else can mint the app token. */
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
