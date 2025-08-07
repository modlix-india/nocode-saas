package com.fincity.saas.message.model.response.call.provider.exotel;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ExotelErrorResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1047165823928346109L;

    @JsonProperty("RestException")
    private RestExc restException;

    @Data
    @Accessors(chain = true)
    public static class RestExc implements Serializable {

        @Serial
        private static final long serialVersionUID = 3994440523176777135L;

        @JsonProperty("Status")
        private int status;

        @JsonProperty("Message")
        private String message;
    }
}
