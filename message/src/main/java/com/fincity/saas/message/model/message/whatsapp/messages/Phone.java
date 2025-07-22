package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.PhoneType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Phone {

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("wa_id")
    private String waId;

    @JsonProperty("type")
    private PhoneType type;

    public String getPhone() {
        return phone;
    }

    public Phone setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getWaId() {
        return waId;
    }

    public Phone setWaId(String waId) {
        this.waId = waId;
        return this;
    }

    public PhoneType getType() {
        return type;
    }

    public Phone setType(PhoneType type) {
        this.type = type;
        return this;
    }
}
