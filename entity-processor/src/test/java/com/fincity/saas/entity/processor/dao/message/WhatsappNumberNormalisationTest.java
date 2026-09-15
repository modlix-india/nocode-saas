package com.fincity.saas.entity.processor.dao.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The comparison that pacing now hangs on.
 *
 * <p>Every counter moved from the bridge session id to the business number, which only works if the
 * several shapes the same number is stored in compare equal. Production holds both
 * {@code +919035898884} and {@code +91 87928 79475}, written by different paths, and an equality
 * test against the raw column matches neither reliably. A number that matches nothing does not fail
 * loudly: it reads as a number with no history, which is exactly the state that allows every send.
 */
class WhatsappNumberNormalisationTest {

    @Test
    @DisplayName("the display and E.164 forms of one number normalise to the same digits")
    void bothStoredFormsAgree() {
        assertEquals(WhatsappMessageDAO.digitsOf("+919035898884"), WhatsappMessageDAO.digitsOf("+91 90358 98884"));
    }

    @Test
    @DisplayName("every separator a number is written with is stripped")
    void stripsPunctuation() {
        assertEquals("919035898884", WhatsappMessageDAO.digitsOf("+91 (903) 589-8884"));
        assertEquals("919035898884", WhatsappMessageDAO.digitsOf("91.9035.898884"));
        assertEquals("919035898884", WhatsappMessageDAO.digitsOf("919035898884"));
    }

    /**
     * Two different numbers must not collapse together. The normaliser removes separators only, so a
     * missing digit stays a different number rather than becoming the same one.
     */
    @Test
    @DisplayName("different numbers stay different")
    void doesNotCollapseDistinctNumbers() {
        String a = WhatsappMessageDAO.digitsOf("+919035898884");
        String b = WhatsappMessageDAO.digitsOf("+919035898885");

        assertEquals(false, a.equals(b));
    }

    @Test
    @DisplayName("a null number stays null rather than becoming the empty string")
    void nullStaysNull() {
        // Empty string would match a row whose number was never written, which is the difference
        // between "this number has no history" and "every row with a missing number is this one".
        assertNull(WhatsappMessageDAO.digitsOf(null));
    }
}
