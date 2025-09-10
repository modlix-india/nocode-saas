package com.modlix.saas.commons2.mq.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.modlix.saas.commons2.mq.events.ContextAuthentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventCreationServiceTest {

    @Mock
    private AmqpTemplate amqpTemplate;

    @InjectMocks
    private EventCreationService eventCreationService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(eventCreationService, "exchange", "test-exchange");
        ReflectionTestUtils.setField(eventCreationService, "routingKey", "events1,events2,events3");
        eventCreationService.init();
    }

    @Test
    public void testCreateEvent_Success() {
        // Given
        EventQueObject queObj = new EventQueObject()
                .setEventName("TEST_EVENT")
                .setClientCode("TEST_CLIENT")
                .setAppCode("TEST_APP")
                .setAuthentication(new ContextAuthentication()
                        .setUser("testuser")
                        .setIsAuthenticated(true)
                        .setClientCode("TEST_CLIENT")
                        .setAppCode("TEST_APP"));

        doNothing().when(amqpTemplate).convertAndSend(anyString(), anyString(), any(EventQueObject.class));

        // When
        boolean result = eventCreationService.createEvent(queObj);

        // Then
        assertTrue(result);
        verify(amqpTemplate).convertAndSend(eq("test-exchange"), anyString(), eq(queObj));
    }

    @Test
    public void testCreateEvent_Exception() {
        // Given
        EventQueObject queObj = new EventQueObject()
                .setEventName("TEST_EVENT")
                .setClientCode("TEST_CLIENT");

        doThrow(new RuntimeException("AMQP Error")).when(amqpTemplate)
                .convertAndSend(anyString(), anyString(), any(EventQueObject.class));

        // When
        boolean result = eventCreationService.createEvent(queObj);

        // Then
        assertFalse(result);
    }

    @Test
    public void testCreateEvent_NullEventObject() {
        // Given
        EventQueObject queObj = null;

        // When
        boolean result = eventCreationService.createEvent(queObj);

        // Then
        assertFalse(result);
    }

    @Test
    public void testCreateEvent_RoutingKeyRotation() {
        // Given
        EventQueObject queObj1 = new EventQueObject().setEventName("EVENT1");
        EventQueObject queObj2 = new EventQueObject().setEventName("EVENT2");
        EventQueObject queObj3 = new EventQueObject().setEventName("EVENT3");
        EventQueObject queObj4 = new EventQueObject().setEventName("EVENT4");

        doNothing().when(amqpTemplate).convertAndSend(anyString(), anyString(), any(EventQueObject.class));

        // When
        boolean result1 = eventCreationService.createEvent(queObj1);
        boolean result2 = eventCreationService.createEvent(queObj2);
        boolean result3 = eventCreationService.createEvent(queObj3);
        boolean result4 = eventCreationService.createEvent(queObj4);

        // Then
        assertTrue(result1);
        assertTrue(result2);
        assertTrue(result3);
        assertTrue(result4);

        // Verify that different routing keys were used (rotation)
        verify(amqpTemplate, times(4)).convertAndSend(eq("test-exchange"), anyString(), any(EventQueObject.class));
    }
}
