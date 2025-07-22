package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactMessage {

    private List<Contact> contacts;

    public List<Contact> getContacts() {
        return contacts;
    }

    public ContactMessage setContacts(List<Contact> contacts) {
        this.contacts = contacts;
        return this;
    }

    public ContactMessage addContacts(Contact contact) {
        if (this.contacts == null) this.contacts = new ArrayList<>();

        this.contacts.add(contact);
        return this;
    }
}
