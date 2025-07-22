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
public class MediaMessage<T extends MediaMessage<T>> {

    @JsonProperty("id")
    private String id;

    @JsonProperty("link")
    private String link;

    public String getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    public T setId(String id) {
        this.id = id;
        return (T) this;
    }

    public String getLink() {
        return link;
    }

    @SuppressWarnings("unchecked")
    public T setLink(String link) {
        this.link = link;
        return (T) this;
    }
}
