package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DateTimeParameter extends Parameter implements Serializable {

    @Serial
    private static final long serialVersionUID = 256160879927777586L;

    @JsonProperty("date_time")
    private DateTime dateTime;

    public DateTimeParameter() {
        super(ParameterType.DATE_TIME);
    }

    public DateTimeParameter(DateTime dateTime) {
        super(ParameterType.DATE_TIME);
        this.dateTime = dateTime;
    }
}
