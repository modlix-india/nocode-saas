package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = -2219087164774254661L;

    @JsonProperty("messaging_product")
    private String messagingProduct = "whatsapp";

    @JsonProperty("status")
    private String status = "read";

    @JsonProperty("message_id")
    private String messageId;
}
