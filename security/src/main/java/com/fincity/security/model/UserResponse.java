package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;

import org.jooq.types.ULong;

import com.fincity.security.dto.User;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 5600650589811219972L;

    private ULong id;
    private ULong clientId;
    private String userName;
    private String emailId;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String middleName;
    private String localeCode;

    public UserResponse(User user) {
        this.id = user.getId();
        this.clientId = user.getClientId();
        this.userName = user.getUserName();
        this.emailId = user.getEmailId();
        this.phoneNumber = user.getPhoneNumber();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.middleName = user.getMiddleName();
        this.localeCode = user.getLocaleCode();
    }
}
