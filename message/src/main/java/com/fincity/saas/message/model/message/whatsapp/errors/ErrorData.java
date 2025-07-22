package com.fincity.saas.message.model.message.whatsapp.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorData(
        @JsonProperty("messaging_product") String messagingProduct,
        @JsonProperty("details") String details,
        // TODO: convert to List<String>
        @JsonProperty("blame_field_specs") Object blameFieldSpecs) {}
