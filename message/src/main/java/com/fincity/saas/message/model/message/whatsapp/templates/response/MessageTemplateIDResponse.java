package com.fincity.saas.message.model.message.whatsapp.templates.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.templates.MessageTemplate;


@Deprecated(forRemoval = true)
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageTemplateIDResponse(@JsonProperty("id") String id) {}
