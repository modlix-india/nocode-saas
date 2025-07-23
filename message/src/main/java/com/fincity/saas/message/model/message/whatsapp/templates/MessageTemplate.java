package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.Category;
import com.fincity.saas.message.model.message.whatsapp.templates.type.LanguageType;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = -2712229854830452961L;

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
}
