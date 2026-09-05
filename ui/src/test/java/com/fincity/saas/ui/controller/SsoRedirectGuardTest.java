package com.fincity.saas.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

/**
 * Guards for /sso/{token}. The endpoint splices redirectUrl into inline JS and then
 * navigates the browser to it, so both the escaping and the destination check matter.
 */
class SsoRedirectGuardTest {

    private static final String OWN_HOST = "sitezump.ai";

    private static ServerHttpRequest requestOn(String forwardedHost) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("https://" + OWN_HOST + "/sso/tkn");
        if (forwardedHost != null)
            builder = builder.header("X-Forwarded-Host", forwardedHost);
        return builder.build();
    }

    /** null means "only a host lookup can settle this", i.e. a cross-origin candidate. */
    private static String guard(String redirectUrl) {
        return UniversalController.decideRedirectWithoutLookup(redirectUrl, OWN_HOST);
    }

    @Test
    void escapesAQuoteSoItCannotCloseTheJsStringLiteral() {

        assertEquals("'\\'+alert(1)+\\''", UniversalController.jsString("'+alert(1)+'"));
        assertEquals("'\\u003c/script\\u003e'", UniversalController.jsString("</script>"));
        assertEquals("''", UniversalController.jsString(null));
    }

    @Test
    void keepsSameOriginDestinations() {

        assertEquals("/", guard("/"));
        assertEquals("/dashboard?tab=1", guard("/dashboard?tab=1"));
        assertEquals("https://sitezump.ai/deals", guard("https://sitezump.ai/deals"));
        // The beacon hop sends the bare origin with no path.
        assertEquals("https://sitezump.ai", guard("https://sitezump.ai"));
        // Host comparison is case-insensitive, as hosts are.
        assertEquals("https://SITEZUMP.AI/x", guard("https://SITEZUMP.AI/x"));
    }

    @Test
    void defersOtherOriginsToTheHostLookupRatherThanGuessing() {

        // Cross-origin is no longer an automatic refusal: the beacon seed lands on one host
        // and continues to an app on another, which is the point of cross-domain SSO. These
        // return null, meaning "ask whether that host belongs to a real app".
        assertNull(guard("https://evil.example/steal"));
        // A userinfo prefix must not spoof the host comparison into passing as same-origin.
        assertNull(guard("https://sitezump.ai@evil.example/steal"));
    }

    @Test
    void rejectsAnythingThatIsNotAnAddressAtAll() {

        // Protocol-relative URLs look like paths but resolve to another origin.
        assertEquals("/", guard("//evil.example/steal"));
        assertEquals("/", guard("/\\evil.example/steal"));
        // Non-http schemes are a script-execution vector, not a destination.
        assertEquals("/", guard("javascript:alert(1)"));
        assertEquals("/", guard(""));
        assertEquals("/", guard(null));
    }

    @Test
    void takesTheClientFacingHostFromTheFirstForwardedHopWithoutItsPort() {

        assertEquals("sitezump.ai", UniversalController.clientFacingHost(requestOn("sitezump.ai:443, internal-lb")));
        assertEquals("sitezump.ai", UniversalController.clientFacingHost(requestOn("sitezump.ai")));
        // With no proxy header the request's own host stands in.
        assertEquals(OWN_HOST, UniversalController.clientFacingHost(requestOn(null)));
    }
}
