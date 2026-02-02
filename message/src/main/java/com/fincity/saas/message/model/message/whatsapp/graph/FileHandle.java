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
public class FileHandle implements Serializable {

    @Serial
    private static final long serialVersionUID = 3622167254576925078L;

    @JsonProperty("h")
    private String handle;
}
