package com.fincity.security.model;

import com.fincity.saas.commons.util.TimeZoneUtil;
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

    /**
     * The IANA time zone this person accepted the invite from, or null to take the client's.
     *
     * <p>Worth carrying separately from the client's own zone: an invited colleague is exactly the
     * case where the two differ, since a tenant does not invite people in order to seat them all in
     * one office.
     */
    private String timeZone;

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

        user.setLocaleCode(this.localeCode);
        // Sanitised, so an unreadable value lands as null and the client's zone applies, rather than
        // as junk that every later reader has to defend against.
        //
        // Kept as a real override even when it matches the client's zone. Unlike the owner at client
        // registration, whose browser told us about the tenant, an invitee's browser tells us about
        // that person: the tenant moving its zone should not relocate them.
        user.setTimeZone(TimeZoneUtil.sanitize(this.timeZone));
        user.setUserName(this.userName);
        user.setEmailId(this.emailId);
        user.setPhoneNumber(this.phoneNumber);
        user.setFirstName(this.firstName);
        user.setLastName(this.lastName);
        user.setMiddleName(this.middleName);

        return user;
    }
}
