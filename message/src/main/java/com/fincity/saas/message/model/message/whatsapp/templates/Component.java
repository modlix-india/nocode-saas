package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ComponentType;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ButtonComponent.class, name = "BUTTONS"), //
    @JsonSubTypes.Type(value = FooterComponent.class, name = "FOOTER"), //
    @JsonSubTypes.Type(value = HeaderComponent.class, name = "HEADER"), //
    @JsonSubTypes.Type(value = BodyComponent.class, name = "BODY")
}) //
public class Component<T extends Component<T>> {
    
    @JsonProperty("type")
    private ComponentType type;

    @JsonProperty("text")
    private String text;

    @JsonProperty("example")
    private Example example;

    
    protected Component() {}

    
    protected Component(ComponentType type) {
        this.type = type;
    }

    
    public String getText() {
        return text;
    }

    
    @SuppressWarnings("unchecked")
    public T setText(String text) {
        this.text = text;
        return (T) this;
    }

    
    public Example getExample() {
        return example;
    }

    
    @SuppressWarnings("unchecked")
    public T setExample(Example example) {
        this.example = example;
        return (T) this;
    }

    
    public ComponentType getType() {
        return type;
    }

    
    public Component<T> setType(ComponentType type) {
        this.type = type;
        return this;
    }
}
