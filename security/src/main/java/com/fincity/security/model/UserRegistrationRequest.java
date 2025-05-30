package com.fincity.security.model;

import com.fincity.security.dto.User;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class UserRegistrationRequest implements BasePassword<UserRegistrationRequest>, Serializable {

    @Serial
    private static final long serialVersionUID = 637282632723L;

    private String localeCode;
    private String userName;
    private String emailId;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String middleName;
    private AuthenticationPasswordType passType;
    private String password;
    private String pin = null;
    private String otp = null;
    private String socialRegisterState;
    private String inviteCode;

    public User getUser() {

        User user = new User();

        user.setLocaleCode(this.getLocaleCode());
        user.setUserName(this.getUserName());
        user.setEmailId(this.getEmailId());
        user.setPhoneNumber(this.getPhoneNumber());
        user.setFirstName(this.getFirstName());
        user.setLastName(this.getLastName());
        user.setMiddleName(this.getMiddleName());
        user.setPassType(this.getPassType());

        user.setPassword(this.getPassword());
        user.setPin(this.getPin());
        user.setOtp(this.getOtp());

        return user;
    }
}
