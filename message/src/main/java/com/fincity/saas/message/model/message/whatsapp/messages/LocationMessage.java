package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocationMessage {

    @JsonProperty("longitude")
    private String longitude;

    @JsonProperty("latitude")
    private String latitude;

    @JsonProperty("name")
    private String name;

    @JsonProperty("address")
    private String address;

    public String getName() {
        return name;
    }

    public LocationMessage setName(String name) {
        this.name = name;
        return this;
    }

    public String getLongitude() {
        return longitude;
    }

    public LocationMessage setLongitude(String longitude) {
        this.longitude = longitude;
        return this;
    }

    public String getLatitude() {
        return latitude;
    }

    public LocationMessage setLatitude(String latitude) {
        this.latitude = latitude;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public LocationMessage setAddress(String address) {
        this.address = address;
        return this;
    }
}
