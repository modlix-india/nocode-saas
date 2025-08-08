package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IOrg implements Serializable {

    @Serial
    private static final long serialVersionUID = 176851365817075585L;

    @JsonProperty("company")
    private String company;

    @JsonProperty("department")
    private String department;

    @JsonProperty("title")
    private String title;
}
