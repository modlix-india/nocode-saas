package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateMessage {
    @JsonProperty("components")
    private List<Component<?>> components;

    @JsonProperty("name")
    private String name;

    @JsonProperty("language")
    private Language language;

    public List<Component<?>> getComponents() {
        return components;
    }

    public TemplateMessage setComponents(List<Component<?>> components) {
        this.components = components;
        return this;
    }

    public String getName() {
        return name;
    }

    public TemplateMessage setName(String name) {
        this.name = name;
        return this;
    }

    public Language getLanguage() {
        return language;
    }

    public TemplateMessage setLanguage(Language language) {
        this.language = language;
        return this;
    }

    public TemplateMessage addComponent(Component<?> component) {
        if (this.components == null) this.components = new ArrayList<>();

        this.components.add(component);
        return this;
    }
}
