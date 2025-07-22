package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;

public class ImageParameter extends Parameter {
    @JsonProperty("image")
    private Image image;

    public ImageParameter() {
        super(ParameterType.IMAGE);
    }

    public ImageParameter(Image image) {
        super(ParameterType.IMAGE);
        this.image = image;
    }

    public Image getImage() {
        return image;
    }

    public ImageParameter setImage(Image image) {
        this.image = image;
        return this;
    }
}
