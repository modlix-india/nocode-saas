package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IWebHookEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -7424547946415662402L;

    @JsonProperty("entry")
    private List<IEntry> entry;

    @JsonProperty("object")
    private String object;
}
