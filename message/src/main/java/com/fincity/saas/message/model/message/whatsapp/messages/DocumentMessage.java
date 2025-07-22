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
public class DocumentMessage extends MediaMessage<DocumentMessage> {
    @JsonProperty("caption")
    private String caption;

    @JsonProperty("filename")
    private String fileName;

    public String getCaption() {
        return caption;
    }

    public DocumentMessage setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public DocumentMessage setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }
}
