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
public final class IButtonReply implements Serializable {

    @Serial
    private static final long serialVersionUID = -509816915115986954L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;
}
