package com.fincity.saas.message.model.message.whatsapp.business;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class BusinessAccount implements Serializable {

    @Serial
    private static final long serialVersionUID = 4579824772953801091L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("timezone_id")
    private String timezoneId;

    @JsonProperty("message_template_namespace")
    private String messageTemplateNamespace;
}
