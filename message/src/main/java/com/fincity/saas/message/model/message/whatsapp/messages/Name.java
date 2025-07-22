package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Name {

    @JsonProperty("prefix")
    private String prefix;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("middle_name")
    private String middleName;

    @JsonProperty("suffix")
    private String suffix;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("formatted_name")
    private String formattedName;

    public String getPrefix() {
        return prefix;
    }

    public Name setPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public Name setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public String getMiddleName() {
        return middleName;
    }

    public Name setMiddleName(String middleName) {
        this.middleName = middleName;
        return this;
    }

    public String getSuffix() {
        return suffix;
    }

    public Name setSuffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    public String getFirstName() {
        return firstName;
    }

    public Name setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getFormattedName() {
        return formattedName;
    }

    public Name setFormattedName(String formattedName) {
        this.formattedName = formattedName;
        return this;
    }
}
