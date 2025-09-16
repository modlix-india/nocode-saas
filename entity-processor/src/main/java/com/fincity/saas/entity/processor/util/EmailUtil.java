package com.fincity.saas.entity.processor.util;

import com.fincity.saas.entity.processor.model.common.Email;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

public class EmailUtil {

    private EmailUtil() {
        // To be used as a static Phone Number utilities class
        throw new IllegalStateException("EmailUtil class");
    }

    public static Email parse(String email) {

        try {
            InternetAddress emailAddr = new InternetAddress(email);
            emailAddr.validate();
        } catch (AddressException ex) {
            return null;
        }

        return new Email().setAddress(email);
    }
}
