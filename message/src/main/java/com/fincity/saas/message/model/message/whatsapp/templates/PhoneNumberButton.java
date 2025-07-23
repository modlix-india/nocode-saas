package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ButtonType;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhoneNumberButton extends Button {

    @Serial
    private static final long serialVersionUID = 5601442561425491593L;

    @JsonProperty("phone_number")
    private String phoneNumber;

    public PhoneNumberButton(String text, String phoneNumber) {
        super(ButtonType.PHONE_NUMBER, text);
        this.phoneNumber = phoneNumber;
    }

    public PhoneNumberButton() {
        super(ButtonType.PHONE_NUMBER);
    }
}
