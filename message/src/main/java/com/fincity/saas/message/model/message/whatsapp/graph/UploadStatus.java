package com.fincity.saas.message.model.message.whatsapp.graph;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadStatus extends BaseId implements Serializable {

    @Serial
    private static final long serialVersionUID = 4749783671668822914L;

    @JsonProperty("file_offset")
    private Long fileOffset;
}
