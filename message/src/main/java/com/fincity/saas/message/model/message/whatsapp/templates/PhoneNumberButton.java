package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ButtonType;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhoneNumberButton extends Button {
    @JsonProperty("phone_number")
    private String phoneNumber;

    
    public PhoneNumberButton(String text, String phoneNumber) {
        super(ButtonType.PHONE_NUMBER, text);
        this.phoneNumber = phoneNumber;
    }

    
    public PhoneNumberButton() {
        super(ButtonType.PHONE_NUMBER);
    }

    
    public String getPhoneNumber() {
        return phoneNumber;
    }

    
    public PhoneNumberButton setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }
}
