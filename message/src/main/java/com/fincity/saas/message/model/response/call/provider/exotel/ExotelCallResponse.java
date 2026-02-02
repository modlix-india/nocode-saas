package com.fincity.saas.message.model.response.call.provider.exotel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class ExotelCallResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 7872369371148938828L;

    @JsonProperty("Call")
    private ExotelCallDetails call;
}
