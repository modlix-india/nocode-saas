package com.fincity.saas.message.service.call.provider.exotel;

import static com.fincity.saas.message.service.call.provider.exotel.ExotelCallService.destinationNumbers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the inbound connect applet is told to ring.
 *
 * <p>This rule changed for <b>every</b> tenant, not only those adopting browser calling: inbound
 * used to ring the number on the agent's profile, and now rings the endpoint rows provisioned for
 * them. A tenant that will never touch a softphone still goes through this code on every inbound
 * call, so the case worth protecting is the one where nothing about them changed.
 *
 * <p>Sequential ringing makes the order load-bearing rather than cosmetic — the provider dials the
 * list in order, so position one answers first and anything later only rings after the ring
 * timeout.
 */
class ExotelInboundRoutingTest {

    private static final String AGENT_SIP = "sip:agentsipid001";
    private static final String AGENT_MOBILE = "+910000000001";
    private static final String PROFILE_PHONE = "+910000000002";

    @Test
    void storedOrderIsPreservedExactly() {

        // The DAO sorts by PRIORITY, so the browser is first and the desk phone second. Rebuilding
        // that order here would drop a third endpoint type or a deliberate re-prioritisation.
        List<String> destinations = destinationNumbers(List.of(AGENT_SIP, AGENT_MOBILE), PROFILE_PHONE);

        assertEquals(List.of(AGENT_SIP, AGENT_MOBILE), destinations, "stored order is the routing rule");
    }

    @Test
    void aTenantWithOnlyAPstnEndpointStillRingsThatAndNothingElse() {

        // The regression case. No browser calling, one PSTN row: it must ring, and the profile
        // number must not be appended — appending it double-rings whenever the two disagree.
        List<String> destinations = destinationNumbers(List.of(AGENT_MOBILE), PROFILE_PHONE);

        assertEquals(List.of(AGENT_MOBILE), destinations, "the provisioned number, not the profile one");
        assertTrue(!destinations.contains(PROFILE_PHONE), "the profile number is a fallback, never an addition");
    }

    @Test
    void noEndpointsFallsBackToTheProfileNumber() {

        // An agent nobody has provisioned. Their profile number is all we know, and a live customer
        // is on the line.
        assertEquals(List.of(PROFILE_PHONE), destinationNumbers(List.of(), PROFILE_PHONE));
    }

    @Test
    void noEndpointsAndNoProfileNumberRingsNothing() {

        // Deliberately empty rather than invented. The applet receives no destination, so the
        // provider plays its own failure treatment instead of dialling a number that is not the
        // agent's — which is the only honest answer when nothing is known about where they are.
        assertTrue(destinationNumbers(List.of(), null).isEmpty(), "nothing known means nothing dialled");
        assertTrue(destinationNumbers(List.of(), "  ").isEmpty(), "a blank profile number is not a number");
    }
}
