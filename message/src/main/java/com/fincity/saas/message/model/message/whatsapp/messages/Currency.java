package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Currency {
    @JsonProperty("fallback_value")
    private String fallbackValue;

    @JsonProperty("code")
    private String code;

    @JsonProperty("amount_1000")
    private long amount1000;

    public Currency() {}

    public Currency(String fallbackValue, String code, long amount1000) {
        this.fallbackValue = fallbackValue;
        this.code = code;
        this.amount1000 = amount1000;
    }

    public String getFallbackValue() {
        return fallbackValue;
    }

    public Currency setFallbackValue(String fallbackValue) {
        this.fallbackValue = fallbackValue;
        return this;
    }

    public String getCode() {
        return code;
    }

    public Currency setCode(String code) {
        this.code = code;
        return this;
    }

    public long getAmount1000() {
        return amount1000;
    }

    public Currency setAmount1000(long amount1000) {
        this.amount1000 = amount1000;
        return this;
    }
}
