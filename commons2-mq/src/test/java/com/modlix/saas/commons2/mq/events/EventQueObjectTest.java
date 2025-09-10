package com.modlix.saas.commons2.mq.events;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EventQueObjectTest {

    @Test
    public void testEventQueObject_DefaultValues() {
        // When
        EventQueObject eventQueObject = new EventQueObject();

        // Then
        assertNull(eventQueObject.getEventName());
        assertNull(eventQueObject.getClientCode());
        assertNull(eventQueObject.getAppCode());
        assertNull(eventQueObject.getXDebug());
        assertNull(eventQueObject.getData());
        assertNull(eventQueObject.getAuthentication());
    }

    @Test
    public void testEventQueObject_ChainedSetters() {
        // Given
        ContextAuthentication auth = new ContextAuthentication()
                .setUser("testuser")
                .setIsAuthenticated(true)
                .setClientCode("TEST_CLIENT")
                .setAppCode("TEST_APP")
                .setAccessToken("test-token");

        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", 123);

        // When
        EventQueObject eventQueObject = new EventQueObject()
                .setEventName("TEST_EVENT")
                .setClientCode("TEST_CLIENT")
                .setAppCode("TEST_APP")
                .setXDebug("debug-info")
                .setData(data)
                .setAuthentication(auth);

        // Then
        assertEquals("TEST_EVENT", eventQueObject.getEventName());
        assertEquals("TEST_CLIENT", eventQueObject.getClientCode());
        assertEquals("TEST_APP", eventQueObject.getAppCode());
        assertEquals("debug-info", eventQueObject.getXDebug());
        assertEquals(data, eventQueObject.getData());
        assertEquals(auth, eventQueObject.getAuthentication());
    }

    @Test
    public void testEventQueObject_Serializable() {
        // Given
        EventQueObject eventQueObject = new EventQueObject()
                .setEventName("TEST_EVENT")
                .setClientCode("TEST_CLIENT")
                .setAppCode("TEST_APP");

        // When & Then
        assertTrue(eventQueObject instanceof java.io.Serializable);
        assertEquals(-2382306278225358489L, eventQueObject.serialVersionUID);
    }

    @Test
    public void testEventQueObject_DataMap() {
        // Given
        Map<String, Object> data = new HashMap<>();
        data.put("stringValue", "test");
        data.put("intValue", 42);
        data.put("booleanValue", true);
        data.put("nullValue", null);

        // When
        EventQueObject eventQueObject = new EventQueObject()
                .setData(data);

        // Then
        assertEquals(data, eventQueObject.getData());
        assertEquals("test", eventQueObject.getData().get("stringValue"));
        assertEquals(42, eventQueObject.getData().get("intValue"));
        assertEquals(true, eventQueObject.getData().get("booleanValue"));
        assertNull(eventQueObject.getData().get("nullValue"));
    }

    @Test
    public void testEventQueObject_EmptyData() {
        // Given
        Map<String, Object> emptyData = new HashMap<>();

        // When
        EventQueObject eventQueObject = new EventQueObject()
                .setData(emptyData);

        // Then
        assertNotNull(eventQueObject.getData());
        assertTrue(eventQueObject.getData().isEmpty());
    }
}
