package com.fincity.saas.message.model.response.call.provider.exotel.integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The envelope every Integrations Core response arrives in.
 *
 * <p>{@code Code} and {@code Status} live in the body, so a 200 at the HTTP layer is not on its own
 * proof of success — check {@link #isSuccess()} before reading {@code Data}. {@code Data} is an
 * object on some endpoints and an array on others, which is why this is generic.
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExotelIntegrationsResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 9033641195712250834L;

    @JsonProperty("RequestId")
    private String requestId;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("Code")
    private Integer code;

    @JsonProperty("Error")
    private String error;

    @JsonProperty("Data")
    private T data;

    public boolean isSuccess() {
        return "Success".equalsIgnoreCase(this.status) && this.data != null;
    }

    /** What to put in front of a caller when the provider refuses. Never the raw envelope. */
    public String errorDetail() {
        if (this.error != null && !this.error.isBlank()) return this.error;
        return "Exotel returned " + this.status + " (" + this.code + ")";
    }
}
