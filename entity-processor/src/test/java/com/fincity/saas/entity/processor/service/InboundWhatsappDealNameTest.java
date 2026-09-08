package com.fincity.saas.entity.processor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a deal created from an inbound WhatsApp message is called.
 *
 * <p>Worth its own test because the input is chosen by whoever sent the message. Anyone who knows the
 * business number can reach the create path with one message, so this string arrives from outside the
 * tenant and lands in a column that is rendered in lists and exported to CSV.
 */
class InboundWhatsappDealNameTest {

    private static PhoneNumber phone() {
        return new PhoneNumber().setCountryCode(91).setNumber("+919902079955");
    }

    @Test
    @DisplayName("uses the customer's WhatsApp profile name when they have one")
    void usesProfileName() {
        assertEquals("Vishwas Kumar", TicketService.dealNameFrom("Vishwas Kumar", phone()));
    }

    /**
     * The behaviour before the profile name was carried through, and still the answer when there is
     * nothing better. A number is a poor name but it identifies the lead.
     */
    @Test
    @DisplayName("falls back to the number when there is no profile name")
    void fallsBackToNumber() {
        assertEquals("+919902079955", TicketService.dealNameFrom(null, phone()));
        assertEquals("+919902079955", TicketService.dealNameFrom("", phone()));
        assertEquals("+919902079955", TicketService.dealNameFrom("   ", phone()));
    }

    /**
     * A name that is only control characters sanitises to nothing, and an empty deal name is unusable
     * in a list. Falling back rather than saving a blank is the point.
     */
    @Test
    @DisplayName("falls back when the name sanitises away to nothing")
    void fallsBackWhenNameIsOnlyControlCharacters() {
        assertEquals("+919902079955", TicketService.dealNameFrom("\n\r\t", phone()));
        assertEquals("+919902079955", TicketService.dealNameFrom("​‮", phone()));
    }

    /**
     * Newlines would break the row a name is rendered on, and an embedded newline in a CSV export is
     * the classic way a display string becomes something else.
     *
     * <p>Note {@code \r\n}: stripping replaces each character with a space, so without collapsing runs
     * the name would keep a double space. That is what this case is really pinning down.
     */
    @Test
    @DisplayName("strips control and format characters and collapses the gaps they leave")
    void stripsControlCharacters() {
        String cleaned = TicketService.dealNameFrom("Vishwas\nKumar\r\n<admin>", phone());

        assertTrue(cleaned.indexOf('\n') < 0, () -> "a newline survived: " + cleaned);
        assertTrue(cleaned.indexOf('\r') < 0, () -> "a carriage return survived: " + cleaned);
        assertEquals("Vishwas Kumar <admin>", cleaned);
    }

    /** A name that is mostly padding reads as two columns in a list unless the runs are collapsed. */
    @Test
    @DisplayName("collapses internal runs of whitespace")
    void collapsesInternalWhitespace() {
        assertEquals("Vishwas Kumar", TicketService.dealNameFrom("Vishwas          Kumar", phone()));
    }

    /**
     * Right-to-left override is what makes a name display as something other than what it is. It is a
     * format character, so the same strip covers it.
     */
    @Test
    @DisplayName("strips a bidirectional override out of a name")
    void stripsBidiOverride() {
        assertEquals("gpj.eman", TicketService.dealNameFrom("‮gpj.eman", phone()));
    }

    /**
     * Bounded well below the column's 512 characters. WhatsApp's own profile-name limit is 25, so
     * anything remotely near this is not a name.
     */
    @Test
    @DisplayName("bounds a very long name rather than storing it whole")
    void boundsLongNames() {
        String name = TicketService.dealNameFrom("x".repeat(4000), phone());

        assertEquals(128, name.length());
    }

    /** Trimmed, so a name padded with spaces does not sort oddly or read as indented. */
    @Test
    @DisplayName("trims surrounding whitespace")
    void trimsWhitespace() {
        assertEquals("Vishwas", TicketService.dealNameFrom("   Vishwas   ", phone()));
    }
}
