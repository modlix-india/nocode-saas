package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.EmailType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Email {

    @JsonProperty("type")
    private EmailType type;

    @JsonProperty("email")
    private String email;

    public Email() {}

    public Email(EmailType type, String email) {
        this.type = type;
        this.email = email;
    }

    public EmailType getType() {
        return type;
    }

    public Email setType(EmailType type) {
        this.type = type;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public Email setEmail(String email) {
        this.email = email;
        return this;
    }
}
