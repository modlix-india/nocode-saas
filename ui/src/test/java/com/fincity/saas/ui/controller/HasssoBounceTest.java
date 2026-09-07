package com.fincity.saas.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;

import org.junit.jupiter.api.Test;

/**
 * The /hassso bounce hands a one-time token to returnUrl, so an open redirect here is an
 * account takeover. Two things stand in the way: the URL must be an absolute http(s) address,
 * and its host must resolve to the very app the token is for. This covers the first.
 */
class HasssoBounceTest {

    @Test
    void acceptsAbsoluteHttpAddresses() {

        URI u = UniversalController.absoluteHttpUri("https://leadzump.ai/deals?x=1");
        assertNotNull(u);
        assertEquals("leadzump.ai", u.getHost());
        assertEquals("https", u.getScheme());

        assertNotNull(UniversalController.absoluteHttpUri("http://leadzump.local.modlix.com:8002/"));
        // Scheme case must not matter; hosts and schemes are case-insensitive.
        assertNotNull(UniversalController.absoluteHttpUri("HTTPS://leadzump.ai/"));
    }

    @Test
    void refusesAnythingThatIsNotAPlaceToSendASession() {

        assertNull(UniversalController.absoluteHttpUri(null));
        assertNull(UniversalController.absoluteHttpUri(""));
        assertNull(UniversalController.absoluteHttpUri("   "));
        // Relative: no host to check against the app, so it cannot be validated at all.
        assertNull(UniversalController.absoluteHttpUri("/deals"));
        assertNull(UniversalController.absoluteHttpUri("//leadzump.ai/deals"));
        // Non-http schemes execute rather than navigate.
        assertNull(UniversalController.absoluteHttpUri("javascript:alert(1)"));
        assertNull(UniversalController.absoluteHttpUri("data:text/html,x"));
        // Unparseable.
        assertNull(UniversalController.absoluteHttpUri("https://exa mple.com/"));
    }

    @Test
    void userinfoCannotSpoofTheHostThatGetsChecked() {

        // The host is what is compared against the app's registered URL, and Java resolves
        // this to the part after the @, not the part before it.
        URI u = UniversalController.absoluteHttpUri("https://leadzump.ai@evil.example/steal");
        assertNotNull(u);
        assertEquals("evil.example", u.getHost());
    }

    @Test
    void portIsCarriedThroughForTheResolverAndAbsentWhenDefault() {

        assertEquals(8002, UniversalController.absoluteHttpUri("http://x.local.modlix.com:8002/a").getPort());
        assertEquals(-1, UniversalController.absoluteHttpUri("https://leadzump.ai/a").getPort());
    }
}
