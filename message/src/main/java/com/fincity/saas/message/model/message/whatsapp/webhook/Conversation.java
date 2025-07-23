package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Conversation implements Serializable {

    @Serial
    private static final long serialVersionUID = -4988213892748536051L;

    @JsonProperty("expiration_timestamp")
    private String expirationTimestamp;

    @JsonProperty("origin")
    private Origin origin;

    @JsonProperty("id")
    private String id;
}
