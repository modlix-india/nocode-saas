package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.CalendarType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DateTime implements Serializable {

    @Serial
    private static final long serialVersionUID = 1707960782396009592L;

    @JsonProperty("fallback_value")
    private String fallbackValue;

    @JsonProperty("calendar")
    private CalendarType calendar;

    @JsonProperty("month")
    private Integer month;

    @JsonProperty("hour")
    private Integer hour;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("day_of_month")
    private Integer dayOfMonth;

    @JsonProperty("day_of_week")
    private Integer dayOfWeek;

    @JsonProperty("minute")
    private Integer minute;
}
