package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.HeaderType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Header {

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

    public Header(HeaderType type) {
        this.type = type;
    }

    public Header() {}

    public HeaderType getType() {
        return type;
    }

    public Header setType(HeaderType type) {
        this.type = type;
        return this;
    }

    public String getText() {
        return text;
    }

    public Header setText(String text) {
        this.text = text;
        return this;
    }

    public Document getDocument() {
        return document;
    }

    public Header setDocument(Document document) {
        this.document = document;
        return this;
    }

    public Image getImage() {
        return image;
    }

    public Header setImage(Image image) {
        this.image = image;
        return this;
    }

    public Video getVideo() {
        return video;
    }

    public Header setVideo(Video video) {
        this.video = video;
        return this;
    }
}
