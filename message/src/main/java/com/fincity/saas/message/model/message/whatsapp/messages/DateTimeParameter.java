package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DateTimeParameter extends Parameter {

    @JsonProperty("date_time")
    private DateTime dateTime;

    public DateTimeParameter() {
        super(ParameterType.DATE_TIME);
    }

    public DateTimeParameter(DateTime dateTime) {
        super(ParameterType.DATE_TIME);

        this.dateTime = dateTime;
    }

    public DateTime getDateTime() {
        return dateTime;
    }

    public DateTimeParameter setDateTime(DateTime dateTime) {
        this.dateTime = dateTime;
        return this;
    }
}
