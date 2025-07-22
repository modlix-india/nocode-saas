package com.fincity.saas.message.model.message.whatsapp.templates.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.templates.Component;
import com.fincity.saas.message.model.message.whatsapp.templates.type.Category;
import java.util.List;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Template(
        @JsonProperty("components") List<Component<?>> components,
        @JsonProperty("name") String name,
        @JsonProperty("language") String language,
        @JsonProperty("id") String id,
        @JsonProperty("category") Category category,
        @JsonProperty("previous_category") Category previousCategory,
        @JsonProperty("status") String status) {}
