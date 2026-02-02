package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.HeaderType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Header implements Serializable {

    @Serial
    private static final long serialVersionUID = 8197688963513345341L;

    @JsonProperty("type")
    private HeaderType type;

    @JsonProperty("text")
    private String text;

    @JsonProperty("document")
    private Document document;

    @JsonProperty("image")
    private Image image;

    @JsonProperty("video")
    private Video video;
}
