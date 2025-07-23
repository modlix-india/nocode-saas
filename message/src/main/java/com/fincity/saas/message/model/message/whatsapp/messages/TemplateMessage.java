package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1723727013932995098L;

    @JsonProperty("components")
    private List<Component<?>> components;

    @JsonProperty("name")
    private String name;

    @JsonProperty("language")
    private Language language;

    public TemplateMessage addComponent(Component<?> component) {
        if (this.components == null) this.components = new ArrayList<>();

        this.components.add(component);
        return this;
    }
}
