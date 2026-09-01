package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The draft hostname is the only gate on an app's unpublished work, so it has to
 * be both a valid DNS label and genuinely unguessable.
 *
 * It is d<32 hex> followed by security.appCodeSuffix and .modlix.com, and
 * deliberately ignores the app's own live URL.
 *
 * The environment comes from appCodeSuffix rather than a draft-specific key: a
 * second per-environment setting meaning almost the same thing is one someone
 * eventually forgets to move, and a draft host silently pointing at the wrong
 * environment is a bad way to find that out. Deriving from the live URL was tried and reverted: apps live
 * on domains other than modlix.com (sitezump.ai, fincity.com, cityville.in and
 * third-party ones), so a derived host would sit somewhere the platform neither
 * controls nor holds a certificate for. A two-label live URL such as theorempro.in
 * produced a name directly under a public suffix, and a live URL whose first label
 * is an environment marker (dev.adzump.ai) produced a host on the production apex.
 * A fixed suffix has none of those failure modes.
 *
 * The label rule has already been got wrong once here: an earlier version built it
 * from UniqueUtil.uniqueName, which appends '_' after every name part. An
 * underscore is illegal in a hostname under RFC 1123, so browsers reject the name
 * and no CA will issue for it, wildcard certificate or not. Every minted URL would
 * have been dead on arrival, and the failure would have looked like a DNS or
 * certificate problem rather than a string-building one.
 */
@DisplayName("Draft hostname generation")
class DraftHostNameTest {

    /** security.appCodeSuffix, the marker every other URL this service builds uses. */
    private static final String ENV = ".dev";

    private static final String SUFFIX = ENV + ".modlix.com";

    private static ClientUrlService serviceForEnv(String appCodeSuffix) {
        ClientUrlService service = new ClientUrlService(null, null, null, null);
        ReflectionTestUtils.setField(service, "appCodeSuffix", appCodeSuffix);
        return service;
    }

    private static String labelOf(String host) {
        return host.substring(0, host.length() - SUFFIX.length());
    }

    @Test
    @DisplayName("the label is a valid DNS label: lowercase alphanumerics only")
    void labelIsValidDnsLabel() {

        ClientUrlService service = serviceForEnv(ENV);

        for (int i = 0; i < 200; i++) {
            String host = service.newDraftHost();

            assertTrue(host.endsWith(SUFFIX), "suffix missing: " + host);
            String label = labelOf(host);

            assertFalse(label.contains("_"), "underscore is illegal in a hostname: " + label);
            assertTrue(label.matches("[a-z0-9]+"), "label must be lowercase alphanumeric only: " + label);
            assertTrue(Character.isLetter(label.charAt(0)), "label must not start with a digit: " + label);
            assertTrue(label.length() <= 63, "DNS labels cap at 63 characters: " + label);
        }
    }

    @Test
    @DisplayName("the host is one label deep, so a one-level wildcard covers it")
    void hostIsOneLabelUnderTheSuffix() {

        String host = serviceForEnv(ENV).newDraftHost();

        // *.dev.modlix.com matches exactly one label. A host two levels down would
        // need a certificate of its own, which is the whole thing this avoids.
        // The suffix's own leading dot is the separator, so the counts match exactly.
        assertEquals(SUFFIX.chars().filter(c -> c == '.').count(),
                host.chars().filter(c -> c == '.').count(),
                "the draft host is more than one label under the suffix: " + host);
    }

    @Test
    @DisplayName("labels carry enough entropy that a large batch never collides")
    void labelsDoNotCollide() {

        ClientUrlService service = serviceForEnv(ENV);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++)
            assertTrue(seen.add(service.newDraftHost()), "generated a duplicate draft hostname");

        assertEquals(5000, seen.size());
    }

    @Test
    @DisplayName("the label is long enough to be unguessable")
    void labelIsLongEnough() {

        // 'd' plus 16 bytes of hex. Anything materially shorter would mean the
        // earlier 32-bits-per-call mistake had crept back.
        assertEquals(33, labelOf(serviceForEnv(ENV).newDraftHost()).length(),
                "expected 128 bits rendered as hex");
    }

    @Test
    @DisplayName("a blank appCodeSuffix is production, not an error")
    void blankSuffixIsProduction() {

        // Production runs with no environment marker, so the draft host is one label
        // under the apex. That is a valid host covered by *.modlix.com, which is why
        // minting no longer pre-flights on a blank suffix the way it did when draft
        // hosts had a key of their own.
        String host = serviceForEnv("").newDraftHost();

        assertTrue(host.endsWith(".modlix.com"), host);
        assertEquals(2, host.chars().filter(c -> c == '.').count(),
                "production draft hosts should be one label under the apex: " + host);
    }

    @Test
    @DisplayName("the app's own live URL has no bearing on the result")
    void hostIsIndependentOfTheApp() {

        // The point of the fixed suffix. An app on ashwa.fincity.com or
        // dev.adzump.ai gets a draft host under the platform's own wildcard, not
        // one under a domain the platform cannot serve.
        String host = serviceForEnv(ENV).newDraftHost();

        assertTrue(host.endsWith(SUFFIX));
        assertFalse(host.contains("fincity.com"));
        assertFalse(host.contains("adzump.ai"));
        assertFalse(host.contains("sitezump.ai"));
    }
}
