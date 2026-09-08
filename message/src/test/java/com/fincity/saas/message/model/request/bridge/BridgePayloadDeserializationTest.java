package com.fincity.saas.message.model.request.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincity.saas.message.enums.bridge.WhatsappSessionState;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parses payloads captured from the Go bridge, byte for byte.
 *
 * <p>The JSON below was produced by marshalling the real {@code RegisterRequest},
 * {@code HeartbeatRequest} and {@code outbox.Event} structs, not written by hand. That distinction
 * is the point of the test: hand-written fixtures encode what someone believed the other side sends,
 * and every interop bug worth catching lives in the gap between that belief and the actual bytes.
 *
 * <p>Regenerate by marshalling those structs in the whatsapp-bridge repo and pasting the output.
 */
class BridgePayloadDeserializationTest {

    /**
     * Configured like Spring Boot's, which is what actually parses these at runtime.
     *
     * <p>{@code JavaTimeModule} matters most: without it {@code Instant} fails outright, and the
     * whole reason these fields are {@code Instant} rather than {@code LocalDateTime} is that Go
     * emits RFC 3339 with a trailing Z that {@code LocalDateTime} refuses.
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    private static final String REGISTER =
            """
            {"instanceId":"inst-in-01","baseUrl":"http://10.1.0.31:8080","countries":["IN"],\
            "sessionCap":25,"version":"abc123","heldSessions":[\
            {"id":"abcdefghijklmnopqrstuv","appCode":"leadzump","clientCode":"FIN","state":"CONNECTED",\
            "phone":"+919876543210","country":"IN","linkedAt":"2026-08-01T09:15:30Z",\
            "stateSince":"2026-08-06T04:05:06Z","sentLastHour":4,"reconnects":1},\
            {"id":"bbcdefghijklmnopqrstuv","appCode":"leadzump","clientCode":"FIN","state":"BANNED",\
            "reason":"connect failure 406","stateSince":"2026-08-06T04:05:06Z","sentLastHour":0,"reconnects":0},\
            {"id":"cbcdefghijklmnopqrstuv","appCode":"leadzump","clientCode":"FIN","state":"PAIRING",\
            "sentLastHour":0,"reconnects":0}]}""";

    private static final String HEARTBEAT =
            """
            {"instanceId":"inst-in-01","activeSessions":2,"heldSessions":3,"sessions":[\
            {"id":"abcdefghijklmnopqrstuv","appCode":"leadzump","clientCode":"FIN","state":"CONNECTED",\
            "phone":"+919876543210","country":"IN","linkedAt":"2026-08-01T09:15:30Z",\
            "stateSince":"2026-08-06T04:05:06Z","sentLastHour":4,"reconnects":1}],\
            "retired":[{"sessionId":"dbcdefghijklmnopqrstuv","state":"LOGGED_OUT",\
            "reason":"logged out for 7 days","phone":"+919000000000","at":"2026-08-06T04:05:06Z"}]}""";

    private static final String EVENTS =
            """
            {"instanceId":"inst-in-01","events":[\
            {"sessionId":"abcdefghijklmnopqrstuv","eventType":"INBOUND_MESSAGE","messageId":"3EB0C1",\
            "messageType":"text","customerWaId":"919812345678","customerPhoneNumber":"+919812345678",\
            "businessPhoneNumber":"+919876543210","from":"919812345678","outbound":false,\
            "bodyText":"is the brochure ready?","occurredAt":"2026-08-06T04:05:06Z","pushName":"Ravi"},\
            {"sessionId":"abcdefghijklmnopqrstuv","eventType":"MESSAGE_STATUS","messageId":"3EB0C2",\
            "messageStatus":"delivered","outbound":true,"occurredAt":"2026-08-06T04:05:06Z"}]}""";

    @Test
    @DisplayName("parses a registration, including every session state the bridge can report")
    void parsesRegistration() throws Exception {

        BridgeRegisterRequest request = this.mapper.readValue(REGISTER, BridgeRegisterRequest.class);

        assertEquals("inst-in-01", request.getInstanceId());
        assertEquals("http://10.1.0.31:8080", request.getBaseUrl());
        assertEquals(java.util.List.of("IN"), request.getCountries());
        assertEquals(25, request.getSessionCap());
        assertEquals(3, request.getHeldSessions().size());

        BridgeSessionSnapshot connected = request.getHeldSessions().get(0);
        assertEquals(WhatsappSessionState.CONNECTED, connected.getState());
        assertEquals("+919876543210", connected.getPhone());
        assertEquals("IN", connected.getCountry());

        // The conversion that silently breaks if these are declared LocalDateTime.
        assertEquals(Instant.parse("2026-08-01T09:15:30Z"), connected.getLinkedAt());
        assertEquals(Instant.parse("2026-08-06T04:05:06Z"), connected.getStateSince());

        assertEquals(WhatsappSessionState.BANNED, request.getHeldSessions().get(1).getState());
        assertEquals(WhatsappSessionState.PAIRING, request.getHeldSessions().get(2).getState());
    }

    /**
     * Go omits zero times entirely rather than sending a zero value.
     *
     * <p>Worth asserting, because a session that has never paired has no {@code linkedAt} and the
     * write path must leave the stored value alone rather than overwriting it with null.
     */
    @Test
    @DisplayName("leaves omitted timestamps null rather than defaulting them")
    void parsesOmittedTimestamps() throws Exception {

        BridgeSessionSnapshot pairing = this.mapper
                .readValue(REGISTER, BridgeRegisterRequest.class)
                .getHeldSessions()
                .get(2);

        assertNull(pairing.getLinkedAt());
        assertNull(pairing.getStateSince());
        assertNull(pairing.getPhone());
    }

    @Test
    @DisplayName("parses a heartbeat and its retirements")
    void parsesHeartbeat() throws Exception {

        BridgeHeartbeatRequest request = this.mapper.readValue(HEARTBEAT, BridgeHeartbeatRequest.class);

        assertEquals(2, request.getActiveSessions());
        assertEquals(3, request.getHeldSessions());
        assertEquals(1, request.getSessions().size());
        assertEquals(1, request.getRetired().size());

        BridgeRetiredSession retired = request.getRetired().get(0);
        assertEquals("dbcdefghijklmnopqrstuv", retired.getSessionId());
        assertEquals(WhatsappSessionState.LOGGED_OUT, retired.getState());
        assertEquals(Instant.parse("2026-08-06T04:05:06Z"), retired.getAt());
    }

    @Test
    @DisplayName("parses an events batch, both a message and a receipt")
    void parsesEvents() throws Exception {

        BridgeEventsRequest request = this.mapper.readValue(EVENTS, BridgeEventsRequest.class);

        assertEquals(2, request.getEvents().size());

        BridgeEvent inbound = request.getEvents().get(0);
        assertEquals(DispatchEventType.INBOUND_MESSAGE, inbound.getEventType());
        assertEquals("3EB0C1", inbound.getMessageId());
        assertEquals("is the brochure ready?", inbound.getBodyText());
        assertFalse(inbound.getOutbound());
        assertNotNull(inbound.getOccurredAt());

        BridgeEvent receipt = request.getEvents().get(1);
        assertEquals(DispatchEventType.MESSAGE_STATUS, receipt.getEventType());
        assertTrue(receipt.getOutbound());
    }

    /**
     * The casing that broke the deal profile once already.
     *
     * <p>These two stay lowercase strings all the way through. entity-processor upper-cases them
     * before resolving its own enum, so this hop tolerates either, but it re-serialises lowercase
     * through {@code @JsonValue} to the UI, where about 150 expressions match on the exact string.
     * Binding them to a Java enum here would re-case them on the way past and blank every chat
     * bubble while every service returned 200.
     */
    @Test
    @DisplayName("keeps messageType and messageStatus lowercase, unbound to any enum")
    void keepsWireCasing() throws Exception {

        BridgeEventsRequest request = this.mapper.readValue(EVENTS, BridgeEventsRequest.class);

        assertEquals("text", request.getEvents().get(0).getMessageType());
        assertEquals("delivered", request.getEvents().get(1).getMessageStatus());
    }

    /** A field the bridge adds later must not take the fleet's state reporting down. */
    @Test
    @DisplayName("ignores a field this service does not know about")
    void toleratesNewFields() throws Exception {

        String withExtra = HEARTBEAT.replace("\"activeSessions\":2", "\"activeSessions\":2,\"somethingNew\":\"x\"");

        assertEquals(2, this.mapper.readValue(withExtra, BridgeHeartbeatRequest.class).getActiveSessions());
    }
}
