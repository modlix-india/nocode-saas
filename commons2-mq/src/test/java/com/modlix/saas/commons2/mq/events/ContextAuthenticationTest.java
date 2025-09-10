package com.modlix.saas.commons2.mq.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContextAuthenticationTest {

    @Test
    public void testContextAuthentication_DefaultValues() {
        // When
        ContextAuthentication auth = new ContextAuthentication();

        // Then
        assertNull(auth.getUser());
        assertFalse(auth.isAuthenticated());
        assertNull(auth.getClientCode());
        assertNull(auth.getAppCode());
        assertNull(auth.getAccessToken());
    }

    @Test
    public void testContextAuthentication_ChainedSetters() {
        // When
        ContextAuthentication auth = new ContextAuthentication()
                .setUser("testuser")
                .setIsAuthenticated(true)
                .setClientCode("TEST_CLIENT")
                .setAppCode("TEST_APP")
                .setAccessToken("test-access-token");

        // Then
        assertEquals("testuser", auth.getUser());
        assertTrue(auth.isAuthenticated());
        assertEquals("TEST_CLIENT", auth.getClientCode());
        assertEquals("TEST_APP", auth.getAppCode());
        assertEquals("test-access-token", auth.getAccessToken());
    }

    @Test
    public void testContextAuthentication_NotAuthenticated() {
        // When
        ContextAuthentication auth = new ContextAuthentication()
                .setUser("testuser")
                .setIsAuthenticated(false)
                .setClientCode("TEST_CLIENT")
                .setAppCode("TEST_APP")
                .setAccessToken("test-access-token");

        // Then
        assertEquals("testuser", auth.getUser());
        assertFalse(auth.isAuthenticated());
        assertEquals("TEST_CLIENT", auth.getClientCode());
        assertEquals("TEST_APP", auth.getAppCode());
        assertEquals("test-access-token", auth.getAccessToken());
    }

    @Test
    public void testContextAuthentication_Serializable() {
        // Given
        ContextAuthentication auth = new ContextAuthentication()
                .setUser("testuser")
                .setIsAuthenticated(true);

        // When & Then
        assertTrue(auth instanceof java.io.Serializable);
        assertEquals(1127850908587759885L, auth.serialVersionUID);
    }

    @Test
    public void testContextAuthentication_NullValues() {
        // When
        ContextAuthentication auth = new ContextAuthentication()
                .setUser(null)
                .setIsAuthenticated(false)
                .setClientCode(null)
                .setAppCode(null)
                .setAccessToken(null);

        // Then
        assertNull(auth.getUser());
        assertFalse(auth.isAuthenticated());
        assertNull(auth.getClientCode());
        assertNull(auth.getAppCode());
        assertNull(auth.getAccessToken());
    }

    @Test
    public void testContextAuthentication_EmptyStrings() {
        // When
        ContextAuthentication auth = new ContextAuthentication()
                .setUser("")
                .setIsAuthenticated(false)
                .setClientCode("")
                .setAppCode("")
                .setAccessToken("");

        // Then
        assertEquals("", auth.getUser());
        assertFalse(auth.isAuthenticated());
        assertEquals("", auth.getClientCode());
        assertEquals("", auth.getAppCode());
        assertEquals("", auth.getAccessToken());
    }
}
