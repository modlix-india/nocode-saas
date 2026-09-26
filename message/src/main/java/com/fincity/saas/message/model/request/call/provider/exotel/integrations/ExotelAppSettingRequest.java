package com.fincity.saas.message.model.request.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Sets one setting on the app. Sent with the app token.
 *
 * <p>A key/value pair, per the vendor's Postman collection. The one that matters here is
 * {@code callback}: browser-placed calls never run the App Bazaar flow, so its Passthru applet never
 * fires for them, and the URL registered here is their only route back with status and recordings.
 *
 * <p>Calling this also creates the settings record the browser SDK reads on startup. Without it,
 * the SDK's own {@code GET /app_setting} returns 404 and the softphone fails before it reaches the
 * registrar.
 */
@Data
@Accessors(chain = true)
public class ExotelAppSettingRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 7726043981150223684L;

    @JsonProperty("Key")
    private String key;

    @JsonProperty("Value")
    private String value;

    public static ExotelAppSettingRequest of(String key, String value) {
        return new ExotelAppSettingRequest().setKey(key).setValue(value);
    }
}
