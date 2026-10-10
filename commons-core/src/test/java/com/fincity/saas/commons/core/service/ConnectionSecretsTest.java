package com.fincity.saas.commons.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A connection read by someone who may not change it keeps where it points and how, and loses every
 * credential: the password or token goes back only to whoever may also replace it.
 */
class ConnectionSecretsTest {

    @Test
    @DisplayName("credentials are removed at any depth, everything else is kept")
    void stripsCredentials() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("appCode", "appbuilder");
        headers.put("Authorization", "Bearer x");
        Map<String, Object> oauth = new LinkedHashMap<>();
        oauth.put("clientId", "id");
        oauth.put("clientSecret", "s");
        oauth.put("tokenUrl", "https://example.com/token");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("baseUrl", "https://example.com/");
        details.put("userName", "kiran");
        details.put("password", "p");
        details.put("defaultHeaders", headers);
        details.put("oauth", oauth);
        details.put("API_KEY", "k");

        @SuppressWarnings("unchecked")
        Map<String, Object> out = ConnectionService.withoutSecrets(details);

        assertEquals("https://example.com/", out.get("baseUrl"));
        assertEquals("kiran", out.get("userName"));
        assertFalse(out.containsKey("password"));
        assertFalse(out.containsKey("API_KEY"));
        @SuppressWarnings("unchecked")
        Map<String, Object> h = (Map<String, Object>) out.get("defaultHeaders");
        assertEquals(Map.of("appCode", "appbuilder"), h);
        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) out.get("oauth");
        assertTrue(o.containsKey("tokenUrl"), "a token URL is an address, not a secret");
        assertFalse(o.containsKey("clientSecret"));
        assertTrue(details.containsKey("password"), "the stored connection is never touched");
    }

    @Test
    void nullDetails() {
        assertNull(ConnectionService.withoutSecrets(null));
    }
}
