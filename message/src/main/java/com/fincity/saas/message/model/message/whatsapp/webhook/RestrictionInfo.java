package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.webhook.type.RestrictionType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RestrictionInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = -7426078095546639706L;

    @JsonProperty("restriction_type")
    private RestrictionType restrictionType;

    @JsonProperty("expiration")
    private String expiration;
}
