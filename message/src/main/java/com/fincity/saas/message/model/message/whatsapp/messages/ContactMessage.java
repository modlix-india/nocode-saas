package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 8046978713161763411L;

    private List<Contact> contacts;

    public ContactMessage addContacts(Contact contact) {
        if (this.contacts == null) this.contacts = new ArrayList<>();

        this.contacts.add(contact);
        return this;
    }
}
