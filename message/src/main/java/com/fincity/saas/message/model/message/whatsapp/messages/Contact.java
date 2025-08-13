package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Contact implements Serializable {

    @Serial
    private static final long serialVersionUID = 2646045562761396235L;

    @JsonProperty("birthday")
    private String birthday;

    @JsonProperty("emails")
    private List<Email> emails;

    @JsonProperty("addresses")
    private List<Address> addresses;

    @JsonProperty("urls")
    private List<Url> urls;

    @JsonProperty("org")
    private Org org;

    @JsonProperty("name")
    private Name name;

    @JsonProperty("phones")
    private List<Phone> phones;

    public Contact addEmail(Email email) {
        if (this.emails == null) this.emails = new ArrayList<>();

        this.emails.add(email);
        return this;
    }

    public Contact addAddress(Address address) {
        if (this.addresses == null) this.addresses = new ArrayList<>();
        this.addresses.add(address);
        return this;
    }

    public Contact addUrl(Url url) {
        if (this.urls == null) this.urls = new ArrayList<>();

        this.urls.add(url);
        return this;
    }

    public Contact addPhone(Phone phone) {
        if (this.phones == null) this.phones = new ArrayList<>();
        this.phones.add(phone);
        return this;
    }
}
