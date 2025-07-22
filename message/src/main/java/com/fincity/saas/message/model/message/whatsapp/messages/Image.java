package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class Image {

    @JsonProperty("id")
    private String id;

    @JsonProperty("link")
    private String link;

    public Image() {}

    public Image(String id, String link) {
        this.id = id;
        this.link = link;
    }

    public String getId() {
        return id;
    }

    public Image setId(String id) {
        this.id = id;
        return this;
    }

    public String getLink() {
        return link;
    }

    public Image setLink(String link) {
        this.link = link;
        return this;
    }
}
