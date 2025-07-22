package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CurrencyParameter extends Parameter {

    @JsonProperty("currency")
    private Currency currency;

    public CurrencyParameter() {
        super(ParameterType.CURRENCY);
    }

    public CurrencyParameter(Currency currency) {
        super(ParameterType.CURRENCY);
        this.currency = currency;
    }

    public Currency getCurrency() {
        return currency;
    }

    public CurrencyParameter setCurrency(Currency currency) {
        this.currency = currency;
        return this;
    }
}
