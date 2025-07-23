package com.fincity.saas.message.model.message.whatsapp.templates.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.templates.Component;
import com.fincity.saas.message.model.message.whatsapp.templates.type.Category;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Template implements Serializable {

    @Serial
    private static final long serialVersionUID = 6625046850444522463L;

    @JsonProperty("components")
    private List<Component<?>> components;

    @JsonProperty("name")
    private String name;

    @JsonProperty("language")
    private String language;

    @JsonProperty("id")
    private String id;

    @JsonProperty("category")
    private Category category;

    @JsonProperty("previous_category")
    private Category previousCategory;

    @JsonProperty("status")
    private String status;
}
