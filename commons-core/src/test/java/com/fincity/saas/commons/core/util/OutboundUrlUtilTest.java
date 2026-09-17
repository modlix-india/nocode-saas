package com.fincity.saas.commons.core.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.exeception.GenericException;

@DisplayName("Outbound URL guard on connection details")
class OutboundUrlUtilTest {

    private static void refuses(String key, String value) {
        assertThrows(GenericException.class,
                () -> OutboundUrlUtil.validateConnectionDetails(Map.of(key, value)),
                () -> key + " = " + value + " should have been refused");
    }

    private static void allows(String key, String value) {
        assertDoesNotThrow(
                () -> OutboundUrlUtil.validateConnectionDetails(Map.of(key, value)),
                () -> key + " = " + value + " should have been allowed");
    }

    @Nested
    @DisplayName("Addresses that are this machine or this network")
    class PrivateTargets {

        @Test
        @DisplayName("loopback and any-local, by name and by number")
        void loopback() {
            List.of("http://localhost/api",
                    "http://localhost:8080/api",
                    "https://LOCALHOST/api",
                    "http://localhost./api",
                    "http://127.0.0.1/api",
                    "http://127.99.1.2/api",
                    "http://[::1]/api",
                    "http://0.0.0.0/api")
                    .forEach(url -> refuses("baseUrl", url));
        }

        @Test
        @DisplayName("RFC 1918 and IPv6 unique local")
        void privateRanges() {
            List.of("http://10.0.0.5/x",
                    "http://172.16.4.1/x",
                    "http://172.31.255.254/x",
                    "http://192.168.1.1/x",
                    "http://[fc00::1]/x",
                    "http://[fd12:3456::1]/x")
                    .forEach(url -> refuses("baseUrl", url));
        }

        @Test
        @DisplayName("the cloud metadata service, by address and by name")
        void metadata() {
            refuses("baseUrl", "http://169.254.169.254/opc/v2/instance/");
            refuses("baseUrl", "http://metadata.google.internal/computeMetadata/v1/");
            refuses("baseUrl", "http://[fe80::1]/x");
        }

        @Test
        @DisplayName("carrier-grade NAT and protocol-assignment ranges")
        void otherNonPublicRanges() {
            refuses("baseUrl", "http://100.64.0.1/x");
            refuses("baseUrl", "http://198.18.0.1/x");
            refuses("baseUrl", "http://192.0.0.1/x");
        }

        @Test
        @DisplayName("our own VCN and the internal-by-convention suffixes")
        void internalSuffixes() {
            refuses("baseUrl", "http://dev-mysql.sub10150624021.modlixvcn.oraclevcn.com:3306/x");
            refuses("baseUrl", "http://core.internal/api");
            refuses("baseUrl", "http://printer.local/api");
            refuses("baseUrl", "http://box.home.arpa/api");
        }

        @Test
        @DisplayName("172.32 is public, so the /12 boundary is not off by one")
        void boundaryIsCorrect() {
            allows("baseUrl", "http://172.32.0.1/x");
            allows("baseUrl", "http://11.0.0.1/x");
        }
    }

    @Nested
    @DisplayName("Schemes")
    class Schemes {

        @Test
        @DisplayName("http and https are the only ones allowed an authority")
        void onlyWeb() {
            allows("baseUrl", "https://api.stripe.com/v1");
            allows("baseUrl", "http://api.example.com/v1");
            refuses("baseUrl", "ftp://files.example.com/x");
            refuses("baseUrl", "gopher://example.com/x");
        }

        @Test
        @DisplayName("a local read dressed up as a URL, with or without an authority")
        void localRead() {
            refuses("baseUrl", "file:///etc/passwd");
            refuses("baseUrl", "file:/etc/passwd");
            refuses("baseUrl", "jar:file:/app.jar!/x");
            refuses("baseUrl", "classpath:application.yml");
        }
    }

    @Nested
    @DisplayName("Where in the map the URL sits")
    class Shape {

        @Test
        @DisplayName("the OAuth2 nested url objects are reached")
        void nested() {
            assertThrows(GenericException.class, () -> OutboundUrlUtil.validateConnectionDetails(Map.of(
                    "baseUrl", "https://api.example.com",
                    "tokenDetails", Map.of("url", "http://127.0.0.1/token", "methodType", "POST"))));
        }

        @Test
        @DisplayName("a URL inside a list is reached")
        void inAList() {
            assertThrows(GenericException.class, () -> OutboundUrlUtil.validateConnectionDetails(Map.of(
                    "endpoints", List.of("https://ok.example.com", "http://192.168.0.9/x"))));
        }

        @Test
        @DisplayName("an SMTP host is a bare host, not a URL, and is still checked")
        void bareHostKeys() {
            assertThrows(GenericException.class, () -> OutboundUrlUtil.validateConnectionDetails(
                    Map.of("mailProps", Map.of("mail.smtp.host", "localhost"))));
            assertThrows(GenericException.class, () -> OutboundUrlUtil.validateConnectionDetails(
                    Map.of("mailProps", Map.of("mail.smtp.host", "10.0.0.4"))));
            assertDoesNotThrow(() -> OutboundUrlUtil.validateConnectionDetails(
                    Map.of("mailProps", Map.of("mail.smtp.host", "smtp.sendgrid.net"))));
        }

        @Test
        @DisplayName("ordinary non-URL values are left alone")
        void nonUrlValuesUntouched() {
            assertDoesNotThrow(() -> OutboundUrlUtil.validateConnectionDetails(Map.of(
                    "userName", "svc-account",
                    "password", "p@ss/word:1",
                    "headerPrefix", "Bearer",
                    "grantType", "client_credentials",
                    "timeout", 30000,
                    "isLifeTimeToken", false,
                    "pagePath", "/settings/integrations",
                    "mailProps", Map.of("mail.smtp.starttls.enable", "true"))));
        }

        @Test
        @DisplayName("null and empty details are not an error")
        void emptyIsFine() {
            assertDoesNotThrow(() -> OutboundUrlUtil.validateConnectionDetails(null));
            assertDoesNotThrow(() -> OutboundUrlUtil.validateConnectionDetails(Map.of()));
        }
    }

    @Nested
    @DisplayName("Things that look like a way around it")
    class Evasion {

        @Test
        @DisplayName("a decimal or hex spelling of loopback")
        void encodedLoopback() {
            refuses("baseUrl", "http://2130706433/x");
            refuses("baseUrl", "http://0x7f000001/x");
            refuses("baseUrl", "http://127.1/x");
        }

        @Test
        @DisplayName("userinfo that makes the host look like somewhere else")
        void userinfoTrick() {
            refuses("baseUrl", "http://api.example.com@127.0.0.1/x");
        }

        @Test
        @DisplayName("an IPv4-mapped IPv6 loopback")
        void mapped() {
            refuses("baseUrl", "http://[::ffff:127.0.0.1]/x");
        }
    }

    @Nested
    @DisplayName("validateResolved, the call-time check")
    class Resolved {

        @Test
        @DisplayName("everything the save-time check refuses, it refuses too")
        void inheritsTheSyntaxRules() {
            List.of("http://localhost:9000/api",
                    "http://127.0.0.1/x",
                    "http://10.0.0.5/x",
                    "http://169.254.169.254/opc/v2/instance/",
                    "ftp://files.example.com/x",
                    "file:///etc/passwd",
                    "http://core.internal/api")
                    .forEach(url -> assertThrows(GenericException.class,
                            () -> OutboundUrlUtil.validateResolved(url), url));
        }

        @Test
        @DisplayName("a name that resolves to loopback is refused on the ADDRESS, not the name")
        void resolvedLoopback() {
            // Not in BLOCKED_NAMES, so the only thing that can refuse it is the
            // lookup -- which is the whole point of doing this at call time.
            GenericException e = assertThrows(GenericException.class,
                    () -> OutboundUrlUtil.validateResolved("http://localhost.localdomain/x"));
            assertTrue(e.getMessage().contains("resolves to")
                            || e.getMessage().contains("does not resolve"),
                    e.getMessage());
        }

        @Test
        @DisplayName("a name that does not resolve at all is refused rather than attempted")
        void unresolvable() {
            GenericException e = assertThrows(GenericException.class,
                    () -> OutboundUrlUtil.validateResolved(
                            "https://no-such-host.invalid/x"));
            assertTrue(e.getMessage().contains("does not resolve"), e.getMessage());
        }

        @Test
        @DisplayName("an address literal is not looked up twice")
        void publicLiteralPasses() {
            // 8.8.8.8 is public and is a literal, so this needs no DNS at all
            // and cannot be flaky on a machine with no network.
            assertDoesNotThrow(() -> OutboundUrlUtil.validateResolved("https://8.8.8.8/x"));
        }
    }

    @Test
    @DisplayName("the refusal names the field and the value, so it can be acted on")
    void messageIsActionable() {
        GenericException e = assertThrows(GenericException.class,
                () -> OutboundUrlUtil.validateConnectionDetails(Map.of("baseUrl", "http://localhost:9000/api")));
        assertTrue(e.getMessage().contains("baseUrl"), e.getMessage());
        assertTrue(e.getMessage().contains("localhost"), e.getMessage());
    }

    @Test
    @DisplayName("isUsable answers instead of throwing, for callers that filter")
    void isUsable() {
        assertTrue(OutboundUrlUtil.isUsable(Map.of("baseUrl", "https://api.example.com")));
        assertTrue(!OutboundUrlUtil.isUsable(Map.of("baseUrl", "http://10.1.2.3")));
    }

    @Test
    @DisplayName("a real production shape passes unchanged")
    void productionShapePasses() {
        assertDoesNotThrow(() -> OutboundUrlUtil.validateConnectionDetails(Map.of(
                "baseUrl", "https://graph.facebook.com/v21.0",
                "userName", "modlix",
                "password", "secret",
                "timeout", 30000,
                "encodingMode", "URI_COMPONENT",
                "defaultHeaders", Map.of("Content-Type", "application/json"))));
    }
}
