package com.fincity.saas.message.model.message.whatsapp.templates.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.response.Paging;
import java.util.List;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageTemplates(@JsonProperty("data") List<Template> data, @JsonProperty("paging") Paging paging) {}
