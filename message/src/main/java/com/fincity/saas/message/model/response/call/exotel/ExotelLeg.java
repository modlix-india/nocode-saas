package com.fincity.saas.message.model.response.call.exotel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.call.exotel.ExotelCallStatus;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ExotelLeg implements Serializable {

    @Serial
    private static final long serialVersionUID = 1546445910808543662L;

    @JsonProperty(value = "id")
    private Integer id;

    @JsonProperty(value = "OnCallDuration")
    private Integer onCallDuration;

    @JsonProperty(value = "Status")
    private ExotelCallStatus status;
}
