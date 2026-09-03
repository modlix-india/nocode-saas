package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fincity.saas.commons.security.model.ClientUrlPattern;

/**
 * The arithmetic behind an app's default hostname, in both directions.
 *
 * An app with no CLIENT_URL row at all is still reachable: when no configured
 * pattern matches a host, resolution strips one of {@code
 * security.subdomain.endings} and serves the remaining label as an appCode. So
 * {@code <appCode><ending>} belongs to the app by convention, and it is the one
 * hostname a CLIENT_URL row must never be allowed to take, because a configured
 * row is matched FIRST.
 *
 * That is not hypothetical. On 2025-02-12 a client registering through cxapp
 * asked for the subdomain `leadzump`, which was free. Two months later the
 * `leadzump` app was created, and from then until this guard existed
 * leadzump.dev.modlix.com served cxapp, with the real app reachable only on
 * leadzumptest.dev.modlix.com. adzump and sitezump went the same way in the same
 * week. Both directions are therefore guarded: no row on an app's hostname, and
 * no app on a code whose hostname is already held.
 */
@DisplayName("An app's default hostname")
class SubdomainHostTest {

    private static final String[] ENDINGS = { ".dev.modlix.com", ".dev.sitezump.ai" };

    private static ClientService serviceWithEndings(String... endings) {
        ClientService service = new ClientService();
        ReflectionTestUtils.setField(service, "subDomainURLEndings", endings);
        return service;
    }

    @Nested
    @DisplayName("host to appCode")
    class HostToAppCode {

        @Test
        @DisplayName("the label under a configured ending is the appCode")
        void labelIsTheAppCode() {

            ClientService service = serviceWithEndings(ENDINGS);

            assertEquals("leadzump", service.subdomainAppCode("leadzump.dev.modlix.com"));
            assertEquals("cxapp", service.subdomainAppCode("cxapp.dev.modlix.com"));
        }

        @Test
        @DisplayName("every configured ending counts, not just the first")
        void everyEndingCounts() {

            // Both endings resolve to an appCode, so an app's hostname on either is
            // equally worth protecting. Guarding only .modlix.com would leave the
            // sitezump.ai one open.
            ClientService service = serviceWithEndings(ENDINGS);

            assertEquals("leadzump", service.subdomainAppCode("leadzump.dev.sitezump.ai"));
        }

        @Test
        @DisplayName("hostnames are case insensitive")
        void caseInsensitive() {

            // `Mybuz.dev.modlix.com` is a real row. An appCode is lowercase by
            // construction, so the label has to be folded or it never matches one.
            assertEquals("mybuz", serviceWithEndings(ENDINGS).subdomainAppCode("Mybuz.dev.modlix.com"));
        }

        @Test
        @DisplayName("a hostname outside every ending belongs to nobody")
        void otherHostsAreNotAppHosts() {

            ClientService service = serviceWithEndings(ENDINGS);

            assertNull(service.subdomainAppCode("dev.leadzump.ai"));
            assertNull(service.subdomainAppCode("app.example.com"));
            assertNull(service.subdomainAppCode("localhost"));
        }

        @Test
        @DisplayName("a prefix spanning more than one label is not an appCode")
        void multiLabelPrefixIsNotAnAppCode() {

            // An appCode cannot contain a dot, so this could only ever have found
            // nothing. Answering null saves the lookup and keeps the guard from
            // refusing a hostname on a code that cannot exist.
            assertNull(serviceWithEndings(ENDINGS).subdomainAppCode("a.b.dev.modlix.com"));
        }

        @Test
        @DisplayName("the ending alone, with no label, is not an app host")
        void bareEndingIsNotAnAppHost() {
            assertNull(serviceWithEndings(ENDINGS).subdomainAppCode("dev.modlix.com"));
        }

        @Test
        @DisplayName("no configured endings means no subdomain hosts at all")
        void noEndingsMeansNoHosts() {

            assertNull(serviceWithEndings().subdomainAppCode("leadzump.dev.modlix.com"));
            assertNull(serviceWithEndings((String[]) null).subdomainAppCode("leadzump.dev.modlix.com"));
        }

        @Test
        @DisplayName("a blank host is not an app host")
        void blankHostIsNotAnAppHost() {

            ClientService service = serviceWithEndings(ENDINGS);

            assertNull(service.subdomainAppCode(null));
            assertNull(service.subdomainAppCode(""));
            assertNull(service.subdomainAppCode("   "));
        }
    }

    @Nested
    @DisplayName("appCode to hosts")
    class AppCodeToHosts {

        @Test
        @DisplayName("one hostname per configured ending")
        void oneHostPerEnding() {

            List<String> hosts = serviceWithEndings(ENDINGS).subdomainHostsOf("leadzump");

            assertEquals(List.of("leadzump.dev.modlix.com", "leadzump.dev.sitezump.ai"), hosts);
        }

        @Test
        @DisplayName("a blank ending is production, and yields the apex host")
        void productionHasNoEnvironmentMarker() {

            // Production runs with no environment marker, so the ending is
            // `.modlix.com` and the app answers one label under the apex.
            assertEquals(List.of("leadzump.modlix.com"),
                    serviceWithEndings(".modlix.com").subdomainHostsOf("leadzump"));
        }

        @Test
        @DisplayName("the two directions agree")
        void roundTrips() {

            ClientService service = serviceWithEndings(ENDINGS);

            for (String host : service.subdomainHostsOf("leadzump"))
                assertEquals("leadzump", service.subdomainAppCode(host),
                        "a host built for an appCode must resolve back to it: " + host);
        }

        @Test
        @DisplayName("nothing to build from means nothing to check")
        void nothingToBuildFrom() {

            assertTrue(serviceWithEndings(ENDINGS).subdomainHostsOf(null).isEmpty());
            assertTrue(serviceWithEndings(ENDINGS).subdomainHostsOf("").isEmpty());
            assertTrue(serviceWithEndings().subdomainHostsOf("leadzump").isEmpty());
        }
    }

    @Nested
    @DisplayName("the hostname inside a stored pattern")
    class PatternHost {

        @Test
        @DisplayName("a bare hostname is its own host")
        void bareHost() {
            assertEquals("leadzump.dev.modlix.com", ClientUrlPattern.hostOf("leadzump.dev.modlix.com"));
        }

        @Test
        @DisplayName("a scheme and a port are not part of the host")
        void schemeAndPortAreStripped() {

            // The column holds whatever was typed. Most rows are bare hosts, but a
            // pattern may carry either, and neither has any bearing on which app a
            // hostname belongs to.
            assertEquals("leadzump.dev.modlix.com", ClientUrlPattern.hostOf("https://leadzump.dev.modlix.com"));
            assertEquals("leadzump.dev.modlix.com", ClientUrlPattern.hostOf("http://leadzump.dev.modlix.com"));
            assertEquals("localhost", ClientUrlPattern.hostOf("http://localhost:8080"));
        }

        @Test
        @DisplayName("case and surrounding space do not change the host")
        void foldedAndTrimmed() {
            assertEquals("leadzump.dev.modlix.com", ClientUrlPattern.hostOf("  LeadZump.Dev.Modlix.Com  "));
        }

        @Test
        @DisplayName("nothing in, nothing out")
        void blankPattern() {

            assertEquals("", ClientUrlPattern.hostOf(null));
            assertEquals("", ClientUrlPattern.hostOf(""));
            assertEquals("", ClientUrlPattern.hostOf("   "));
        }
    }
}
