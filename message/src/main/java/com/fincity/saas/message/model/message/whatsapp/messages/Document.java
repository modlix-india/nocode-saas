package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class Document {

    @JsonProperty("id")
    private String id;

    @JsonProperty("link")
    private String link;

    @JsonProperty("filename")
    private String fileName;

    public Document(String id, String link, String fileName) {
        this.id = id;
        this.link = link;
        this.fileName = fileName;
    }

    public Document() {}

    public String getId() {
        return id;
    }

    public Document setId(String id) {
        this.id = id;
        return this;
    }

    public String getLink() {
        return link;
    }

    public Document setLink(String link) {
        this.link = link;
        return this;
    }

    public String getFileName() {
        return fileName;
    }

    public Document setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }
}
