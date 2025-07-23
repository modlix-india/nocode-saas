package com.fincity.saas.message.model.message.whatsapp.media;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 4022384869893101571L;

    @JsonProperty("id")
    private String id;
}
