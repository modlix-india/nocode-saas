package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.LevelType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(value = Include.NON_NULL)
public final class Throughput implements Serializable {

    @Serial
    private static final long serialVersionUID = 2319385361544743605L;

    @JsonProperty("level")
    private LevelType level;
}
