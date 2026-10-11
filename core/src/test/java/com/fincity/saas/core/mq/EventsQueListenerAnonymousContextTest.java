package com.fincity.saas.core.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;

/**
 * Every scheduler tick and one-off is raised without authentication, so it runs under the
 * anonymous context built here. urlAppCode and urlClientCode must not be swapped: a swap sends
 * a scheduled function's Storage call to a client named after the app, and makes ScheduleEvent /
 * CancelScheduledEvent read the event definition under the client code as the app.
 */
class EventsQueListenerAnonymousContextTest {

    @Test
    void anonymousContextCarriesAppAndClientInTheRightFields() {

        Client client = new Client()
                .setId(BigInteger.valueOf(7))
                .setCode("FIN")
                .setTypeCode("BUS")
                .setLevelType("CLIENT");

        ContextAuthentication ca = EventsQueListener.makeAnonymousContextAuth("leadzumptwo", "FIN", client);

        assertEquals("leadzumptwo", ca.getUrlAppCode());
        assertEquals("FIN", ca.getUrlClientCode());
        assertEquals("FIN", ca.getClientCode());
        assertEquals("FIN", ca.getLoggedInFromClientCode());
        assertEquals(BigInteger.valueOf(7), ca.getUser().getClientId());
        assertNull(ca.getVerifiedAppCode());
        assertFalse(ca.isAuthenticated());
    }

    @Test
    void anonymousContextCarriesTheEventRunnerAuthority() {

        Client client = new Client().setId(BigInteger.ONE).setCode("FIN");

        ContextAuthentication ca = EventsQueListener.makeAnonymousContextAuth("leadzumptwo", "FIN", client);

        assertEquals(List.of("Authorities._Anonymous", "Authorities._Event_Runner"),
                ca.getUser().getStringAuthorities());
        // What CoreFunctionService.execute checks for a function's executeAuth.
        assertTrue(SecurityContextUtil.hasAuthority("Authorities._Event_Runner", ca.getAuthorities()));
        assertTrue(SecurityContextUtil.hasAuthority("Authorities._Anonymous", ca.getAuthorities()));
        assertFalse(SecurityContextUtil.hasAuthority("Authorities.Logged_IN", ca.getAuthorities()));
        // A browser's anonymous context holds only _Anonymous, so the gate refuses it.
        assertFalse(SecurityContextUtil.hasAuthority("Authorities._Event_Runner",
                List.of("Authorities._Anonymous")));
    }

    @Test
    void urlClientCodeFollowsTheEventNotTheResolvedClient() {

        // The event's clientCode is what the scheduler fanned out to; it is passed through as-is.
        Client client = new Client().setId(BigInteger.ONE).setCode("FIN2");

        ContextAuthentication ca = EventsQueListener.makeAnonymousContextAuth("appA", "FIN2", client);

        assertEquals("appA", ca.getUrlAppCode());
        assertEquals("FIN2", ca.getUrlClientCode());
    }
}
