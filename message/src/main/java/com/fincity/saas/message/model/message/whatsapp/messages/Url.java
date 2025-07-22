package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.UrlType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Url {

    @JsonProperty("type")
    private UrlType type;

    @JsonProperty("url")
    private String url;

    public UrlType getType() {
        return type;
    }

    public Url setType(UrlType type) {
        this.type = type;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public Url setUrl(String url) {
        this.url = url;
        return this;
    }
}
