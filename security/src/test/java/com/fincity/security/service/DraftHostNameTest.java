package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The draft hostname is the only gate on an app's unpublished work, so it has to
 * be both a valid DNS label and genuinely unguessable.
 *
 * The label rule has already been got wrong once in this feature: an earlier
 * version built it from UniqueUtil.uniqueName, which appends '_' after every name
 * part. An underscore is illegal in a hostname under RFC 1123, so browsers reject
 * the name and no CA will issue for it, wildcard certificate or not. Every minted
 * URL would have been dead on arrival, and the failure would have looked like a
 * DNS or certificate problem rather than a string-building one.
 */
@DisplayName("Draft hostname generation")
class DraftHostNameTest {

    private static final String SUFFIX = ".dev.modlix.com";

    private String newHost(ClientUrlService service) throws Exception {
        Method m = ClientUrlService.class.getDeclaredMethod("newDraftHost");
        m.setAccessible(true);
        return (String) m.invoke(service);
    }

    private ClientUrlService serviceWithSuffix(String suffix) {
        ClientUrlService service = new ClientUrlService(null, null, null, null);
        ReflectionTestUtils.setField(service, "draftUrlSuffix", suffix);
        return service;
    }

    @Test
    @DisplayName("the label is a valid DNS label: lowercase alphanumerics only")
    void labelIsValidDnsLabel() throws Exception {

        ClientUrlService service = serviceWithSuffix(SUFFIX);

        for (int i = 0; i < 200; i++) {
            String host = newHost(service);

            assertTrue(host.endsWith(SUFFIX), "suffix missing: " + host);
            String label = host.substring(0, host.length() - SUFFIX.length());

            assertFalse(label.contains("_"), "underscore is illegal in a hostname: " + label);
            assertTrue(label.matches("[a-z0-9]+"), "label must be lowercase alphanumeric only: " + label);
            assertTrue(Character.isLetter(label.charAt(0)), "label must not start with a digit: " + label);
            assertTrue(label.length() <= 63, "DNS labels cap at 63 characters: " + label);
        }
    }

    @Test
    @DisplayName("labels carry enough entropy that a large batch never collides")
    void labelsDoNotCollide() throws Exception {

        ClientUrlService service = serviceWithSuffix(SUFFIX);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++)
            assertTrue(seen.add(newHost(service)), "generated a duplicate draft hostname");

        assertEquals(5000, seen.size());
    }

    @Test
    @DisplayName("the label is long enough to be unguessable")
    void labelIsLongEnough() throws Exception {

        String host = newHost(serviceWithSuffix(SUFFIX));
        String label = host.substring(0, host.length() - SUFFIX.length());

        // 'd' plus 16 bytes of hex. Anything materially shorter would mean the
        // earlier 32-bits-per-call mistake had crept back.
        assertEquals(33, label.length(), "expected 128 bits rendered as hex, got: " + label);
    }
}
