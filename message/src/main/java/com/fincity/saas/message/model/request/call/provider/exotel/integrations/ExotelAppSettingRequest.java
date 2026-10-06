package com.fincity.saas.message.model.request.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Sets one setting on the app. Sent with the app token. {@code callback} matters most: browser-placed calls
 * never run the App Bazaar flow, so this URL is their only route back with status and recordings.
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
