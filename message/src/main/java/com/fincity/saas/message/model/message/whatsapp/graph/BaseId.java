package com.fincity.saas.message.model.message.whatsapp.graph;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1079516856485512246L;

    @JsonProperty("id")
    private String id;
}
