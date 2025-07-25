package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoParameter extends Parameter implements Serializable {

    @Serial
    private static final long serialVersionUID = 6457867065214210659L;

    @JsonProperty("video")
    private Video video;

    public VideoParameter() {
        super(ParameterType.VIDEO);
    }

    public VideoParameter(ParameterType type, Video video) {
        super(type);
        this.video = video;
    }
}
