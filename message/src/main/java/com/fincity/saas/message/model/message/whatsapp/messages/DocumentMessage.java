package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentMessage extends MediaMessage<DocumentMessage> {

    @Serial
    private static final long serialVersionUID = 3024115756608764552L;

    @JsonProperty("caption")
    private String caption;

    @JsonProperty("filename")
    private String fileName;
}
