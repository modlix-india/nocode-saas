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
public final class IContact implements Serializable {

    @Serial
    private static final long serialVersionUID = 8533519492207359501L;

    @JsonProperty("profile")
    private IProfile profile;

    @JsonProperty("name")
    private IName name;

    @JsonProperty("phones")
    private List<IPhone> phones;

    @JsonProperty("wa_id")
    private String waId;
}
