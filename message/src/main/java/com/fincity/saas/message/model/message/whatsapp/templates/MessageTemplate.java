package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.Category;
import com.fincity.saas.message.model.message.whatsapp.templates.type.LanguageType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageTemplate {
    
    private String name;
    
    private LanguageType languageType;
    
    private Category category;
    
    private List<Component<?>> components;

    
    public MessageTemplate addComponent(Component<?> component) {
        if (this.components == null) this.components = new ArrayList<>();

        this.components.add(component);
        return this;
    }

    
    public MessageTemplate addComponents(Component<?>... components) {
        if (this.components == null) this.components = new ArrayList<>();
        if (components != null) this.components.addAll(Arrays.stream(components).toList());
        return this;
    }

    
    public String getName() {
        return name;
    }

    
    public MessageTemplate setName(String name) {
        this.name = name;
        return this;
    }

    
    public LanguageType getLanguage() {
        return languageType;
    }

    
    public MessageTemplate setLanguage(LanguageType languageType) {
        this.languageType = languageType;
        return this;
    }

    
    public Category getCategory() {
        return category;
    }

    
    public MessageTemplate setCategory(Category category) {
        this.category = category;
        return this;
    }

    
    public List<Component<?>> getComponents() {
        return components;
    }

    
    public MessageTemplate setComponents(List<Component<?>> components) {
        this.components = components;
        return this;
    }
}
