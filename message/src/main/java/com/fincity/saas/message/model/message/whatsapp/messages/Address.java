package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.AddressType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Address implements Serializable {

    @Serial
    private static final long serialVersionUID = 6956973960386534666L;

    @JsonProperty("zip")
    private String zip;

    @JsonProperty("country")
    private String country;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("city")
    private String city;

    @JsonProperty("street")
    private String street;

    @JsonProperty("state")
    private String state;

    @JsonProperty("type")
    private AddressType type;
}
