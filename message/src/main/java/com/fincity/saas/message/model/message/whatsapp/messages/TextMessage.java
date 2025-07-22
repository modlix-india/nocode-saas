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
public class TextMessage {

    @JsonProperty("preview_url")
    private boolean previewUrl;

    @JsonProperty("body")
    private String body;

    public boolean isPreviewUrl() {
        return previewUrl;
    }

    public TextMessage setPreviewUrl(boolean previewUrl) {
        this.previewUrl = previewUrl;
        return this;
    }

    public String getBody() {
        return body;
    }

    public TextMessage setBody(String body) {
        this.body = body;
        return this;
    }
}
