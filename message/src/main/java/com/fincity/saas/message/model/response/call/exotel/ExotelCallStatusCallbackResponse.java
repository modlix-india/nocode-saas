package com.fincity.saas.message.model.response.call.exotel;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class ExotelCallStatusCallbackResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;

    private String message;

    public static ExotelCallStatusCallbackResponse success() {
        return new ExotelCallStatusCallbackResponse().setSuccess(true).setMessage("Callback processed successfully");
    }

    public static ExotelCallStatusCallbackResponse error(String message) {
        return new ExotelCallStatusCallbackResponse().setSuccess(false).setMessage(message);
    }
}
