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
public final class Contact implements Serializable {

    @Serial
    private static final long serialVersionUID = 8533519492207359501L;

    @JsonProperty("profile")
    private Profile profile;

    @JsonProperty("name")
    private Name name;

    @JsonProperty("phones")
    private List<Phone> phones;

    @JsonProperty("wa_id")
    private String waId;
}
