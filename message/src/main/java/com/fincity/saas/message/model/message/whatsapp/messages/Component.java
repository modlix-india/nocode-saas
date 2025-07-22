package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ComponentType;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ButtonComponent.class, name = "button"), //
    @JsonSubTypes.Type(value = HeaderComponent.class, name = "header"), //
    @JsonSubTypes.Type(value = BodyComponent.class, name = "body")
})
public abstract class Component<T extends Component<T>> {

    @JsonProperty("type")
    private ComponentType type;

    @JsonProperty("parameters")
    private List<Parameter> parameters;

    protected Component() {}

    protected Component(ComponentType componentType) {
        this.type = componentType;
    }

    public Component<T> addParameter(Parameter parameter) {
        if (this.parameters == null) this.parameters = new ArrayList<>();

        this.parameters.add(parameter);

        return this;
    }
}
