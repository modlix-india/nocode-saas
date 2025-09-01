package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.Category;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.ParameterFormat;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.SubCategory;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ComponentType;
import com.fincity.saas.message.model.message.whatsapp.templates.type.HeaderFormat;
import com.fincity.saas.message.model.message.whatsapp.templates.type.LanguageType;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = -2712229854830452961L;

    @JsonProperty("name")
    private String name;

    @JsonProperty("allow_category_change")
    private boolean allowCategoryChange;

    @JsonProperty("category")
    private Category category;

    @JsonProperty("sub_category")
    private SubCategory subCategory;

    @JsonProperty("message_send_ttl_seconds")
    private String messageSendTtlSeconds;

    @JsonProperty("parameter_format")
    private ParameterFormat parameterFormat = ParameterFormat.POSITIONAL;

    @JsonProperty("language")
    private LanguageType language;

    @JsonProperty("components")
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

    public MessageTemplate setMessageSendTtlSeconds(Long sendTtlSeconds) {

        if (sendTtlSeconds != null && sendTtlSeconds > 0) {
            if (this.category == null)
                throw new GenericException(HttpStatus.BAD_REQUEST, "Category must be set before setting TTL");

            this.category.validateTtl(sendTtlSeconds);
            this.messageSendTtlSeconds = String.valueOf(sendTtlSeconds);
        } else {
            this.messageSendTtlSeconds = String.valueOf(this.category.getDefaultTtlSec());
        }

        return this;
    }

    public boolean hadHeaderMediaFile() {
        if (this.components == null || this.components.isEmpty()) return false;
        return components.stream()
                .anyMatch(c -> c.getType() == ComponentType.HEADER
                        && c instanceof HeaderComponent h
                        && h.getFormat() != HeaderFormat.TEXT);
    }
}
