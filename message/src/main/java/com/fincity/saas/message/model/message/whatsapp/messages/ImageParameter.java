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
public class ImageParameter extends Parameter implements Serializable {

    @Serial
    private static final long serialVersionUID = 6762335199892814556L;

    @JsonProperty("image")
    private Image image;

    public ImageParameter() {
        super(ParameterType.IMAGE);
    }

    public ImageParameter(Image image) {
        super(ParameterType.IMAGE);
        this.image = image;
    }
}
