package com.fincity.saas.entity.processor.model.request.ticket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether a create request names the person it is for.
 *
 * <p>A request that names nobody used to go through, and the owner lookup, finding no phone or email
 * to filter on, matched the client's newest owner. The deal then took that owner's number. The case
 * that exposed it: American Samoa picked from the dial codes with a ten digit US number typed after
 * it, which does not parse, sent from a form whose email was left blank.
 */
class TicketRequestIdentifyInfoTest {

    @Test
    @DisplayName("a number that does not parse and a blank email identify nobody")
    void unparseablePhoneAndBlankEmail() {
        PhoneNumber phone = PhoneNumber.of("+16848776766565");
        assertNull(phone);

        TicketRequest request = new TicketRequest().setPhoneNumber(phone).setEmail(Email.of(""));

        assertFalse(request.hasIdentifyInfo());
    }

    @Test
    @DisplayName("dial code and number sent as one string is enough")
    void phoneWithDialCodeInOneString() {
        TicketRequest request =
                new TicketRequest().setPhoneNumber(PhoneNumber.of("+18776766565")).setEmail(Email.of(""));

        assertTrue(request.hasIdentifyInfo());
    }

    @Test
    @DisplayName("an email alone is enough")
    void emailAlone() {
        TicketRequest request = new TicketRequest().setEmail(Email.of("someone@example.com"));

        assertTrue(request.hasIdentifyInfo());
    }

    @Test
    @DisplayName("blank values do not count")
    void blankValues() {
        TicketRequest request =
                new TicketRequest().setPhoneNumber(new PhoneNumber().setNumber(" ")).setEmail(Email.of(" "));

        assertFalse(request.hasIdentifyInfo());
    }
}
