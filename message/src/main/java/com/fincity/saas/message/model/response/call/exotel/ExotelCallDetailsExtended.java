package com.fincity.saas.message.model.response.call.exotel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.call.exotel.ExotelCallStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class ExotelCallDetailsExtended implements Serializable {

    @Serial
    private static final long serialVersionUID = 6936806395046152143L;

    @JsonProperty("ConversationDuration")
    private Integer conversationDuration;

    @JsonProperty("Leg1Status")
    private ExotelCallStatus leg1Status;

    @JsonProperty("Leg2Status")
    private ExotelCallStatus leg2Status;

    @JsonProperty("Legs")
    private List<LegWrapper> legs;

    @Data
    @Accessors(chain = true)
    @FieldNameConstants
    public static class LegWrapper implements Serializable {

        @Serial
        private static final long serialVersionUID = 4460723164076004257L;

        @JsonProperty("Leg")
        private ExotelLeg leg;
    }
}
