package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;

public class VideoParameter extends Parameter {
    @JsonProperty("video")
    private Video video;

    public VideoParameter() {
        super(ParameterType.VIDEO);
    }

    public VideoParameter(ParameterType type, Video video) {
        super(type);
        this.video = video;
    }

    public Video getVideo() {
        return video;
    }

    public VideoParameter setVideo(Video video) {
        this.video = video;
        return this;
    }
}
