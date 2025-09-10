package com.modlix.saas.commons2.mq;

import org.junit.jupiter.api.Test;
import com.modlix.saas.commons2.mq.events.EventNames;
import com.modlix.saas.commons2.mq.events.exception.EventCreationException;

import static org.junit.jupiter.api.Assertions.*;

public class Commons2MqTest {

    @Test
    public void testEventNames() {
        assertEquals("CLIENT_CREATED", EventNames.CLIENT_CREATED);
        assertEquals("USER_CREATED", EventNames.USER_CREATED);
        assertEquals("CLIENT_REGISTERED", EventNames.CLIENT_REGISTERED);
        assertEquals("USER_REGISTERED", EventNames.USER_REGISTERED);
    }

    @Test
    public void testEventNamesGetEventName() {
        String result = EventNames.getEventName("USER_$_CHANGED", "PASSWORD");
        assertEquals("USER_PASSWORD_CHANGED", result);

        result = EventNames.getEventName("USER_RESET_$_REQUEST", "PASSWORD");
        assertEquals("USER_RESET_PASSWORD_REQUEST", result);

        result = EventNames.getEventName("SIMPLE_EVENT");
        assertEquals("SIMPLE_EVENT", result);
    }

    @Test
    public void testEventCreationException() {
        EventCreationException exception = new EventCreationException("eventName");
        assertEquals("eventName cannot be null or blank", exception.getMessage());
    }
}
