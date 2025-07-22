package com.fincity.saas.message.model.message.whatsapp.messages.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Contact {

    @JsonProperty("input")
    private String input;

    @JsonProperty("wa_id")
    private String waId;
}
