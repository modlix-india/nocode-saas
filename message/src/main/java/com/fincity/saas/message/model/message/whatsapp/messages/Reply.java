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
public class Reply implements Serializable {

    @Serial
    private static final long serialVersionUID = -245719745582594426L;

    @JsonProperty("id")
    public String id;

    @JsonProperty("title")
    public String title;
}
