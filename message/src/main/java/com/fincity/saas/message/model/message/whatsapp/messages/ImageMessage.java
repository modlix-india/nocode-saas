package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageMessage extends MediaMessage<ImageMessage> {
    @JsonProperty("caption")
    private String caption;

    public String getCaption() {
        return caption;
    }

    public ImageMessage setCaption(String caption) {
        this.caption = caption;
        return this;
    }
}
