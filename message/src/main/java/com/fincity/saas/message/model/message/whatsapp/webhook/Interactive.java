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
public final class Interactive implements Serializable {

    @Serial
    private static final long serialVersionUID = 4856168925797203607L;

    @JsonProperty("list_reply")
    private ListReply listReply;

    @JsonProperty("type")
    private String type;

    @JsonProperty("button_reply")
    private ButtonReply buttonReply;
}
