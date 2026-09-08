package com.fincity.saas.entity.processor.dto.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.entity.processor.oserver.message.enums.call.ExotelCallStatus;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks the wire shape of a call.
 *
 * <p>Not a formality. Moving calls out of the message service changed which Java class produces this
 * JSON, and the deal profile binds these field names directly with no schema between them, so a
 * rename here fails silently in the browser rather than loudly in a build. Each assertion below
 * corresponds to a binding on the page.
 */
class CallSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("direction serialises as `outbound`, not `isOutbound`")
    void outboundFieldName() throws Exception {

        Map<String, Object> json = this.asMap(new Call().setOutbound(true));

        // Lombok gives a primitive `boolean isOutbound` the getter isOutbound(), which Jackson
        // publishes as "outbound". A Boolean would publish "isOutbound" instead and every call
        // would read as inbound in the UI without anything failing.
        assertTrue(json.containsKey("outbound"), "expected `outbound`, got: " + json.keySet());
        assertFalse(json.containsKey("isOutbound"), "`isOutbound` means the field became a Boolean");
        assertEquals(Boolean.TRUE, json.get("outbound"));
    }

    @Test
    @DisplayName("provider status serialises in the provider's own hyphenated form")
    void providerStatusDisplayForm() throws Exception {

        Map<String, Object> json = this.asMap(new Call().setExotelCallStatus(ExotelCallStatus.IN_PROGRESS));

        // The page renders Parent.exotelCallStatus straight to the screen, so the @JsonValue on
        // getDisplayName() is what an agent actually reads. Losing it would show IN_PROGRESS.
        assertEquals("in-progress", json.get("exotelCallStatus"));
    }

    @Test
    @DisplayName("raw provider payloads keep the provider's key casing")
    void providerPayloadKeysArePreserved() throws Exception {

        Call call = new Call()
                .setExotelCallResponse(Map.of("Call", Map.of("StartTime", "2026-08-04 10:15:00")))
                .setExotelConnectAppletRequest(Map.of("StartTime", "2026-08-04 10:15:00"));

        Map<String, Object> json = this.asMap(call);

        // The page reads Parent.exotelCallResponse.Call.StartTime. Storing these as Maps rather
        // than porting the provider's model classes is what keeps that path intact.
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) json.get("exotelCallResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) response.get("Call");

        assertEquals("2026-08-04 10:15:00", inner.get("StartTime"));

        @SuppressWarnings("unchecked")
        Map<String, Object> applet = (Map<String, Object>) json.get("exotelConnectAppletRequest");
        assertEquals("2026-08-04 10:15:00", applet.get("StartTime"));
    }

    @Test
    @DisplayName("a later event cannot walk a finished call backwards")
    void mergeDoesNotRegressTerminalStatus() {

        Call existing = new Call().setExotelCallStatus(ExotelCallStatus.COMPLETED).setDuration(42L);

        // A stray ringing callback arriving after completion describes a moment that has passed.
        existing.merge(new Call().setExotelCallStatus(ExotelCallStatus.IN_PROGRESS));

        assertEquals(ExotelCallStatus.COMPLETED, existing.getExotelCallStatus());
    }

    @Test
    @DisplayName("a partial event does not blank what an earlier one recorded")
    void mergeKeepsFieldsTheEventDidNotMention() {

        Call existing = new Call()
                .setProviderCallId("sid-1")
                .setFrom("9999999999")
                .setCustomerPhoneNumber("9999999999")
                .setRecordingUrl("https://example.invalid/rec.mp3");

        // Status callbacks carry no phone numbers. Replacing wholesale would erase them.
        existing.merge(new Call().setProviderCallId("sid-1").setDuration(30L));

        assertEquals("9999999999", existing.getFrom());
        assertEquals("9999999999", existing.getCustomerPhoneNumber());
        assertEquals("https://example.invalid/rec.mp3", existing.getRecordingUrl());
        assertEquals(30L, existing.getDuration());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Call call) throws Exception {
        return this.objectMapper.readValue(this.objectMapper.writeValueAsString(call), Map.class);
    }
}
