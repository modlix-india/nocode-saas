package com.fincity.saas.message.model.message.whatsapp.templates.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.response.Paging;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MessageTemplates implements Serializable {

    @Serial
    private static final long serialVersionUID = 8923095017182390156L;

    @JsonProperty("data")
    private List<Template> data;

    @JsonProperty("paging")
    private Paging paging;
}
