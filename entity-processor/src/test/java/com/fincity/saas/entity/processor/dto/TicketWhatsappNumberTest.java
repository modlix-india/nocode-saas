package com.fincity.saas.entity.processor.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fallback that every WhatsApp send now depends on.
 *
 * <p>Worth its own test because the failure it guards against is invisible. A deal with no separate
 * WhatsApp number - which is nearly all of them - has a null in that column, so any code path that
 * reads the field directly addresses its message to nobody and reports success. Nothing throws and
 * nothing is logged; the message simply never arrives.
 */
class TicketWhatsappNumberTest {

    @Test
    @DisplayName("falls back to the phone number when no WhatsApp number is recorded")
    void fallsBackToPhone() {

        Ticket ticket = new Ticket().setDialCode(91).setPhoneNumber("+919740485795");

        assertEquals("+919740485795", ticket.whatsappOrPhoneNumber());
        assertEquals(91, ticket.whatsappOrPhoneDialCode());
        assertFalse(ticket.hasSeparateWhatsappNumber());
    }

    @Test
    @DisplayName("prefers the WhatsApp number, with its own dial code, once one is recorded")
    void prefersWhatsappNumber() {

        Ticket ticket = new Ticket()
                .setDialCode(91)
                .setPhoneNumber("+914066778899")
                .setWhatsappDialCode(1)
                .setWhatsappNumber("+14155550123");

        assertEquals("+14155550123", ticket.whatsappOrPhoneNumber());
        // The pair has to move together. Taking the number from one column and the dial code from
        // the other is how a US number ends up being paced against Indian quiet hours.
        assertEquals(1, ticket.whatsappOrPhoneDialCode());
        assertTrue(ticket.hasSeparateWhatsappNumber());
    }

    @Test
    @DisplayName("treats a blank WhatsApp number as absent rather than as a destination")
    void blankIsAbsent() {

        Ticket ticket = new Ticket()
                .setDialCode(91)
                .setPhoneNumber("+919740485795")
                .setWhatsappNumber("   ");

        // A cleared form field arrives as an empty string, not as a null. Honouring it would send to
        // nowhere while the row looks like someone set it deliberately.
        assertEquals("+919740485795", ticket.whatsappOrPhoneNumber());
        assertEquals(91, ticket.whatsappOrPhoneDialCode());
        assertFalse(ticket.hasSeparateWhatsappNumber());
    }

    @Test
    @DisplayName("carries both numbers through the copy constructor")
    void copyConstructorCarriesBoth() {

        Ticket original = new Ticket()
                .setDialCode(91)
                .setPhoneNumber("+914066778899")
                .setWhatsappDialCode(91)
                .setWhatsappNumber("+919740485795");

        Ticket copy = new Ticket(original);

        // The copy constructor is used on every update path, so a field missing from it is silently
        // reset to null the first time anyone edits the deal.
        assertEquals("+919740485795", copy.getWhatsappNumber());
        assertEquals(91, copy.getWhatsappDialCode());
        assertEquals("+919740485795", copy.whatsappOrPhoneNumber());
    }

    @Test
    @DisplayName("answers null when the deal has neither number")
    void noNumberAtAll() {

        Ticket ticket = new Ticket().setPhoneNumber(null);

        assertNull(ticket.whatsappOrPhoneNumber());
        assertFalse(ticket.hasSeparateWhatsappNumber());
    }
}
