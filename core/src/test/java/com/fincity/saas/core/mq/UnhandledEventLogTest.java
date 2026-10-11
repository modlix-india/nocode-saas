package com.fincity.saas.core.mq;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.mq.events.EventQueObject;

/**
 * An event with no EventAction is dropped by core. For the message service's hand-off events that
 * loses a customer's message or call, so it must be warned about every time, naming the app, the
 * client and the event; any other event is warned about once an hour per event, app and client.
 */
class UnhandledEventLogTest {

    private static EventQueObject event(String name, String app, String client) {
        return new EventQueObject().setEventName(name).setAppCode(app).setClientCode(client);
    }

    @Test
    void aHandoffEventIsWarnedAboutEveryTimeNamingAppClientAndEvent() {

        UnhandledEventLog log = new UnhandledEventLog();

        for (String name : UnhandledEventLog.HANDOFF_EVENTS) {
            for (int i = 0; i < 3; i++) {
                String w = log.warning(event(name, "leadzumptwo", "FIN"), 1000L + i);
                assertNotNull(w);
                assertTrue(w.contains("'" + name + "'"), w);
                assertTrue(w.contains("app 'leadzumptwo'"), w);
                assertTrue(w.contains("client 'FIN'"), w);
                assertTrue(w.contains("EventAction named " + name), w);
            }
        }
    }

    @Test
    void anyOtherEventIsWarnedAboutOncePerHourPerEventAppAndClient() {

        UnhandledEventLog log = new UnhandledEventLog();

        String first = log.warning(event("USER_CREATED", "leadzumptwo", "FIN"), 0L);
        assertNotNull(first);
        assertTrue(first.contains("'USER_CREATED'") && first.contains("app 'leadzumptwo'")
                && first.contains("client 'FIN'"), first);

        assertNull(log.warning(event("USER_CREATED", "leadzumptwo", "FIN"), 1000L));

        // A different client, app or event is its own key.
        assertNotNull(log.warning(event("USER_CREATED", "leadzumptwo", "FIN2"), 1000L));
        assertNotNull(log.warning(event("USER_CREATED", "leadzump", "FIN"), 1000L));
        assertNotNull(log.warning(event("CLIENT_CREATED", "leadzumptwo", "FIN"), 1000L));

        // And the same key again once the hour is up.
        assertNotNull(log.warning(event("USER_CREATED", "leadzumptwo", "FIN"),
                UnhandledEventLog.REPEAT_AFTER_MILLIS));
    }

    @Test
    void nothingToSayAboutNoEvent() {
        assertNull(new UnhandledEventLog().warning(null, 0L));
    }
}
