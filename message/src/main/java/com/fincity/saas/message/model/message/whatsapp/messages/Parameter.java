package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextParameter.class, name = "text"),
    @JsonSubTypes.Type(value = CurrencyParameter.class, name = "currency"),
    @JsonSubTypes.Type(value = DateTimeParameter.class, name = "date_time"),
    @JsonSubTypes.Type(value = ImageParameter.class, name = "image"),
    @JsonSubTypes.Type(value = VideoParameter.class, name = "video"),
    @JsonSubTypes.Type(value = DocumentParameter.class, name = "document"),
    @JsonSubTypes.Type(value = ButtonPayloadParameter.class, name = "payload")
})
public class Parameter implements Serializable {

    @Serial
    private static final long serialVersionUID = 2482963597070332814L;

    @JsonProperty("type")
    private ParameterType type;

    @JsonProperty("parameter_name")
    private String parameterName;

    protected Parameter(ParameterType type) {
        this.type = type;
    }
}
