package com.fincity.saas.message.model.request.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** TeleCMI names and types the same field differently between events and CDRs (guide §5.3). */
class TelecmiWebhookTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static TelecmiWebhook read(String json) throws Exception {
        return TelecmiWebhook.of(MAPPER.readTree(json));
    }

    @Test
    void theAppIdIsReadUnderEitherName() throws Exception {
        assertEquals("1111112", read("{\"app_id\":1111112}").appId());
        assertEquals("1111112", read("{\"appid\":1111112}").appId());
        assertEquals("1111112", read("{\"appid\":\"1111112\"}").appId());
        assertNull(read("{}").appId());
    }

    @Test
    void theCallCodeIsReadFromAnObjectOrAJsonStringAndNothingElse() throws Exception {
        assertEquals(
                "CODE1", read("{\"extra_params\":{\"callCode\":\"CODE1\"}}").callCode());
        assertEquals(
                "CODE1",
                read("{\"extra_params\":\"{\\\"callCode\\\":\\\"CODE1\\\"}\"}").callCode());
        // What the SDK-placed call's events carried.
        assertNull(read("{\"extra_params\":{\"{}\":null}}").callCode());
        assertNull(read("{\"extra_params\":\"not json\"}").callCode());
        assertNull(read("{\"extra_params\":{\"callCode\":42}}").callCode());
        assertNull(read("{}").callCode());
    }

    @Test
    void aMissedCdrHasNoTalkTime() throws Exception {
        assertEquals(0L, read("{\"type\":\"cdr\",\"status\":\"missed\"}").answeredSeconds());
        assertEquals(0L, read("{\"answeredsec\":null}").answeredSeconds());
        assertEquals(9L, read("{\"answeredsec\":9}").answeredSeconds());
    }

    @Test
    void aRecordingIsOnlyTakenWhenTheCdrSaysItWasRecorded() throws Exception {
        assertEquals("R.mp3", read("{\"record\":true,\"filename\":\"R.mp3\"}").recordingFile());
        assertNull(read("{\"record\":false,\"filename\":\"R.mp3\"}").recordingFile());
        assertNull(read("{\"filename\":\"R.mp3\"}").recordingFile());
    }

    @Test
    void theTypeIsReadCaseInsensitively() throws Exception {
        assertTrue(read("{\"type\":\"CDR\"}").isCdr());
        assertFalse(read("{\"type\":\"event\"}").isCdr());
        assertFalse(TelecmiWebhook.of(null).isCdr());
    }

    @Test
    void blankAndStructuredValuesReadAsAbsent() throws Exception {
        assertNull(read("{\"request_id\":\"  \"}").requestId());
        assertNull(read("{\"status\":{\"x\":1}}").status());
        assertNull(read("{\"time\":\"soon\"}").time());
    }
}
