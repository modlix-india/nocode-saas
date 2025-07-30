package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.message.MessageStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = -1534840853790734401L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("conversation")
    private IConversation conversation;

    @JsonProperty("pricing")
    private IPricing pricing;

    @JsonProperty("recipient_id")
    private String recipientId;

    @JsonProperty("status")
    private MessageStatus status;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("errors")
    private List<IError> errors;
}
