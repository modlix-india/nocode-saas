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
public class Video implements Serializable {

    @Serial
    private static final long serialVersionUID = 6254147501869384041L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("link")
    private String link;
}
