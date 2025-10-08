package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.message.provider.whatsapp.business.phone.type.Score;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class QualityScore implements Serializable {

    @Serial
    private static final long serialVersionUID = 4887049058043096034L;

    @JsonProperty("date")
    private Integer date;

    @JsonProperty("reasons")
    private List<String> reasons;

    @JsonProperty("score")
    private Score score;
}
