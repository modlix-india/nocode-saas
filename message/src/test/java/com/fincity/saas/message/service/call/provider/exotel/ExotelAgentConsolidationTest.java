package com.fincity.saas.message.service.call.provider.exotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import java.util.List;
import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;

/**
 * Collapsing destination rows into the agents they belong to.
 *
 * <p>The table stores one row per destination, because ringing is sequential and {@code PRIORITY}
 * is the order the provider dials. A listing built from those rows shows the same person twice —
 * once for their browser, once for their phone — which is how an operator ends up deactivating
 * "the other one".
 *
 * <p>The cases worth pinning are the untidy ones: an agent missing a destination, an agent whose
 * rows were deactivated, and an endpoint type this code does not know. Each has a defensible answer
 * and a tempting wrong one.
 */
class ExotelAgentConsolidationTest {

    private static final ULong AGENT = ULong.valueOf(4405);
    private static final ULong OTHER_AGENT = ULong.valueOf(4406);

    private static ProviderUserEndpoint endpoint(ULong userId, String type, String value, boolean active) {

        ProviderUserEndpoint endpoint = new ProviderUserEndpoint()
                .setEndpointType(type)
                .setEndpointValue(value)
                .setProviderUserId("agent@example.com")
                .setVirtualNumber("+910000000001");

        endpoint.setUserId(userId).setActive(active);

        return endpoint;
    }

    @Test
    void twoDestinationsBecomeOneAgent() {

        List<ProvisionedAgent> agents = ExotelIntegrationsService.consolidate(List.of(
                endpoint(AGENT, "WEBRTC_SIP", "sip:agentsipid001", true),
                endpoint(AGENT, "PSTN_PHONE", "+910000000002", true)));

        assertEquals(1, agents.size(), "one person, one row");
        assertEquals("sip:agentsipid001", agents.getFirst().getSipEndpoint(), "the browser destination");
        assertEquals("+910000000002", agents.getFirst().getAgentNumber(), "and the phone fallback");
        assertEquals("+910000000001", agents.getFirst().getVirtualNumber(), "the number customers see");
        assertTrue(agents.getFirst().isActive());
    }

    @Test
    void separateAgentsStaySeparate() {

        List<ProvisionedAgent> agents = ExotelIntegrationsService.consolidate(List.of(
                endpoint(AGENT, "WEBRTC_SIP", "sip:agentsipid001", true),
                endpoint(OTHER_AGENT, "WEBRTC_SIP", "sip:agentsipid002", true)));

        assertEquals(2, agents.size(), "grouping is per user id, not per endpoint type");
        assertEquals(AGENT, agents.getFirst().getUserId(), "and the DAO's ordering survives");
    }

    @Test
    void anAgentWithOnlyAPhoneIsShownRatherThanHidden() {

        // Half-provisioned is exactly what a settings screen needs to surface. Returning nothing,
        // or inventing a SIP endpoint, would hide the one thing an operator has to fix.
        List<ProvisionedAgent> agents =
                ExotelIntegrationsService.consolidate(List.of(endpoint(AGENT, "PSTN_PHONE", "+910000000002", true)));

        assertEquals(1, agents.size());
        assertNull(agents.getFirst().getSipEndpoint(), "no softphone, and the response says so");
        assertEquals("+910000000002", agents.getFirst().getAgentNumber());
    }

    @Test
    void deactivatedRowsProduceAnInactiveAgent() {

        List<ProvisionedAgent> agents = ExotelIntegrationsService.consolidate(List.of(
                endpoint(AGENT, "WEBRTC_SIP", "sip:agentsipid001", false),
                endpoint(AGENT, "PSTN_PHONE", "+910000000002", false)));

        assertFalse(agents.getFirst().isActive(), "every destination retired means the agent is");
    }

    @Test
    void oneLiveDestinationKeepsTheAgentActive() {

        // Any, not all. Deactivation clears an agent's rows together, so a mix means something
        // partial happened — and the agent is still reachable, which is what a screen must say.
        List<ProvisionedAgent> agents = ExotelIntegrationsService.consolidate(List.of(
                endpoint(AGENT, "WEBRTC_SIP", "sip:agentsipid001", false),
                endpoint(AGENT, "PSTN_PHONE", "+910000000002", true)));

        assertTrue(agents.getFirst().isActive(), "still reachable on one destination");
    }

    @Test
    void anUnknownEndpointTypeIsIgnoredRatherThanGuessed() {

        // ENDPOINT_TYPE is a string so a third destination kind needs no migration. Until this code
        // knows what one means, putting it in the SIP or phone field would be worse than omitting it
        // — the agent still appears, with the destinations that are understood.
        List<ProvisionedAgent> agents = ExotelIntegrationsService.consolidate(List.of(
                endpoint(AGENT, "WEBRTC_SIP", "sip:agentsipid001", true),
                endpoint(AGENT, "SOMETHING_NEW", "whatever:0001", true)));

        assertEquals(1, agents.size());
        assertEquals("sip:agentsipid001", agents.getFirst().getSipEndpoint());
        assertNull(agents.getFirst().getAgentNumber(), "an unrecognised type lands nowhere");
    }
}
