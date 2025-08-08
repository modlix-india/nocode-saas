package com.fincity.saas.message.model.message.whatsapp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphCommerceSettings implements Serializable {

    @Serial
    private static final long serialVersionUID = 5381551015510413339L;

    @JsonProperty("data")
    private List<CommerceDataItem> data;
}
