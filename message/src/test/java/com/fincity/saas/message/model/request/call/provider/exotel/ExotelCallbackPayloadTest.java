package com.fincity.saas.message.model.request.call.provider.exotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.enums.call.provider.exotel.ExotelCallStatus;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * The two callback shapes Exotel actually sends, pinned against live payloads.
 *
 * <p>Exotel reaches this service through two engines that report the same call differently. The
 * Integrations engine handles browser-placed calls and posts JSON; the App Bazaar flow handles
 * inbound and posts form-encoded fields. They disagree on nearly every name that matters —
 * {@code CallStatus} against {@code DialCallStatus}, {@code TotalDuration} against
 * {@code DialCallDuration}, {@code CallRecordings} against {@code RecordingUrl} — and they disagree
 * on timestamp format too.
 *
 * <p><b>Every one of those mismatches failed silently.</b> An unmapped key is not an error: Jackson
 * drops it, the form binder ignores it, and the row keeps whatever it was created with. That is how
 * completed calls sat at {@code IN_PROGRESS} with no duration and no recording while the webhook
 * returned 200 and the logs stayed clean. Nothing surfaces these but a test holding the real
 * payloads, which is what this is — each assertion below corresponds to a defect that reached a
 * live call.
 *
 * <p>Payloads keep the exact shape of production callbacks — field names, formats, and the quirks
 * below — with identifiers, numbers and addresses replaced by placeholders, since none of that is
 * what the tests assert. When Exotel renames something, this fails here rather than in a call log
 * somebody notices weeks later.
 */
class ExotelCallbackPayloadTest {

    /** Live JSON from the Integrations engine, for a browser-placed call that completed. */
    private static final String WEBRTC_CALLBACK =
            """
            {
              "CustomerId": "00000000-0000-4000-8000-000000000001",
              "AppId": "00000000-0000-4000-8000-000000000002",
              "CallSid": "11111111111111111111111111111111",
              "AppUserID": "agent@example.com",
              "VirtualNumber": "+910000000001",
              "Direction": "outbound",
              "CallStatus": "completed",
              "CallState": "terminal",
              "ToNumber": "0000000002",
              "FromNumber": "sip:agentsipid001",
              "TotalDuration": 13,
              "CallRecordings": "https://recordings.mum1.exotel.com/exotelrecordings/accountsid/11111111111111111111111111111111.mp3",
              "CallDetail": "terminal",
              "CreatedAt": "0001-01-01T00:00:00Z",
              "UpdatedAt": "0001-01-01T00:00:00Z",
              "StartTime": "2026-09-04T19:21:01+05:30",
              "EndTime": "2026-09-04T19:21:27+05:30"
            }
            """;

    /**
     * The telephony engine's own callback, which click-to-call still runs on.
     *
     * <p>Here as a regression guard rather than for its own sake. Every alias added for the
     * integrations engine lands on this same class, and an alias that captured a name this payload
     * also uses would break click-to-call silently — the older flow, the one already in production.
     * Note the timestamps: space-separated, where the integrations engine sends an offset.
     */
    private static final String TELEPHONY_CALLBACK =
            """
            {
              "CallSid": "33333333333333333333333333333333",
              "Status": "completed",
              "ConversationDuration": 42,
              "RecordingUrl": "https://recordings.mum1.exotel.com/exotelrecordings/accountsid/33333333333333333333333333333333.mp3",
              "StartTime": "2026-09-04 18:49:45",
              "EndTime": "2026-09-04 18:50:10"
            }
            """;

    private ObjectMapper mapper() {
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Live form fields from the App Bazaar Passthru applet, for an inbound call the agent answered.
     *
     * <p>Note what is not here: no {@code CallStatus}, and an {@code EndTime} of epoch zero standing
     * in for "not set". The outcome arrives as {@code DialCallStatus}, and the talk time only as an
     * indexed leg entry.
     */
    private static MultiValueMap<String, String> passthruCallback() {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("CallSid", "22222222222222222222222222222222");
        form.add("CallFrom", "0000000003");
        form.add("CallTo", "0000000001");
        form.add("CallType", "completed");
        form.add("DialCallStatus", "completed");
        form.add("DialCallDuration", "51");
        form.add("DialWhomNumber", "sip:agentsipid001");
        form.add("Direction", "incoming");
        form.add("StartTime", "2026-09-05 23:10:37");
        form.add("EndTime", "1970-01-01 05:30:00");
        form.add("OutgoingPhoneNumber", "0000000001");
        form.add(
                "RecordingUrl",
                "https://recordings.mum1.exotel.com/exotelrecordings/accountsid/22222222222222222222222222222222.mp3");
        form.add("Legs[0][Number]", "sip:agentsipid001");
        form.add("Legs[0][OnCallDuration]", "50");
        form.add("Legs[0][CallerId]", "0000000001");
        form.add("Legs[1][Number]", "0000000004");
        form.add("Legs[1][OnCallDuration]", "0");

        return form;
    }

    @Test
    void webrtcCallbackMapsItsOwnFieldNames() throws Exception {

        ExotelCallStatusCallback callback = this.mapper().readValue(WEBRTC_CALLBACK, ExotelCallStatusCallback.class);

        assertEquals("11111111111111111111111111111111", callback.getCallSid(), "CallSid");
        assertEquals(ExotelCallStatus.COMPLETED, callback.getStatus(), "CallStatus, where this class expects Status");
        assertEquals(13L, callback.getConversationDuration(), "TotalDuration, where this class expects it");
        assertNotNull(callback.getRecordingUrl(), "CallRecordings, where this class expects RecordingUrl");
        assertEquals("sip:agentsipid001", callback.getFrom(), "FromNumber, where this class expects From");
    }

    @Test
    void webrtcCallbackReadsIsoOffsetTimestamps() throws Exception {

        ExotelCall call = new ExotelCall();
        call.update(this.mapper().readValue(WEBRTC_CALLBACK, ExotelCallStatusCallback.class));

        assertNotNull(call.getStartTime(), "ISO-8601 with an offset, as the integrations engine sends");
        assertNotNull(call.getEndTime(), "and its end, which is after its start here");
    }

    @Test
    void epochPlaceholderTimestampsAreNotStored() throws Exception {

        ExotelCall call = new ExotelCall();
        call.update(this.mapper().readValue(WEBRTC_CALLBACK, ExotelCallStatusCallback.class));

        // CreatedAt arrives as 0001-01-01T00:00:00Z. Stored, it is a date nobody meant.
        assertNull(call.getDateCreated(), "a year-zero timestamp means 'not set'");
    }

    @Test
    void endBeforeStartIsDiscarded() throws Exception {

        // Observed live: a call reported as ending eight seconds before it began. One of the two is
        // wrong and the payload does not say which, so the corroborated start is kept.
        String inverted = WEBRTC_CALLBACK
                .replace("\"StartTime\": \"2026-09-04T19:21:01+05:30\"", "\"StartTime\": \"2026-09-05T22:51:24+05:30\"")
                .replace("\"EndTime\": \"2026-09-04T19:21:27+05:30\"", "\"EndTime\": \"2026-09-05T22:51:16+05:30\"");

        ExotelCall call = new ExotelCall();
        call.update(this.mapper().readValue(inverted, ExotelCallStatusCallback.class));

        assertNotNull(call.getStartTime(), "the start is corroborated by when the dial was placed");
        assertNull(call.getEndTime(), "an end before its own start is not a time");
    }

    @Test
    void webrtcCallbackBringsADialTimeRowToItsFinishedState() throws Exception {

        // The row as the dial left it: the Sid is known, the outcome is not. Parsing correctly is
        // only half of it — what mattered live was that none of it reached the row, which sat at
        // IN_PROGRESS with no duration and no recording while the webhook returned 200.
        ExotelCall row = new ExotelCall()
                .setSid("11111111111111111111111111111111")
                .setExotelCallStatus(ExotelCallStatus.IN_PROGRESS);

        row.update(this.mapper().readValue(WEBRTC_CALLBACK, ExotelCallStatusCallback.class));

        assertEquals(ExotelCallStatus.COMPLETED, row.getExotelCallStatus(), "status must move off IN_PROGRESS");
        assertNotNull(row.getRecordingUrl(), "the recording must land on the row, not just parse");
        assertEquals(13L, row.getConversationDuration(), "and so must the talk time");
    }

    @Test
    void telephonyCallbackStillLandsAfterTheIntegrationsAliases() throws Exception {

        ExotelCall row = new ExotelCall().setSid("33333333333333333333333333333333");

        row.update(this.mapper().readValue(TELEPHONY_CALLBACK, ExotelCallStatusCallback.class));

        assertEquals(ExotelCallStatus.COMPLETED, row.getExotelCallStatus(), "click-to-call must keep working");
        assertEquals(42L, row.getConversationDuration(), "on its own field name, not the aliased one");
        assertNotNull(row.getStartTime(), "space-separated timestamps, as the telephony engine sends");
        assertEquals(25L, row.getDuration(), "total duration derived from a start and end that both parsed");
    }

    @Test
    void spaceSeparatedTimestampWithAnOffsetParses() throws Exception {

        // The third shape the provider sends, and the one browser calls arrive with: space
        // separated like the telephony API, but carrying an offset like the integrations engine.
        // A strict pattern rejected it and the null was written over a start time the dial had
        // already recorded, so the total duration fell back to talk time and under-reported.
        String spacedOffset = WEBRTC_CALLBACK
                .replace("\"StartTime\": \"2026-09-04T19:21:01+05:30\"", "\"StartTime\": \"2026-09-04 19:21:01+05:30\"")
                .replace("\"EndTime\": \"2026-09-04T19:21:27+05:30\"", "\"EndTime\": \"2026-09-04 19:21:27+05:30\"");

        ExotelCall row = new ExotelCall().setSid("11111111111111111111111111111111");

        row.update(this.mapper().readValue(spacedOffset, ExotelCallStatusCallback.class));

        assertNotNull(row.getStartTime(), "space-separated with an offset must parse");
        assertNotNull(row.getEndTime(), "and so must its end");
        assertEquals(26L, row.getDuration(), "total is the wall clock, not the 13s of talk time");
        assertEquals(13L, row.getConversationDuration(), "talk time stays what the provider reported");
    }

    @Test
    void passthruReportsTheOutcomeAsDialCallStatus() {

        ExotelPassThruCallback callback = ExotelPassThruCallback.of(passthruCallback());

        assertEquals(ExotelCallStatus.COMPLETED, callback.getDialCallStatus(), "DialCallStatus");
        assertNull(callback.getCallStatus(), "this payload carries no CallStatus at all");
        assertEquals("sip:agentsipid001", callback.getDialWhomNumber(), "where the call actually landed");
    }

    @Test
    void passthruLegsAreReassembledFromIndexedKeys() {

        ExotelPassThruCallback callback = ExotelPassThruCallback.of(passthruCallback());

        assertNotNull(callback.getLegs(), "Legs[i][key] entries, which no form binder reassembles alone");
        assertEquals(2, callback.getLegs().size(), "one leg per destination tried");
        assertEquals("50", callback.getLegs().getFirst().get("OnCallDuration"), "the answered leg's talk time");
    }

    @Test
    void inboundPassthruLandsStatusDestinationAndBothDurations() {

        ExotelCall call = new ExotelCall().setExotelCallStatus(ExotelCallStatus.IN_PROGRESS);

        call.update(ExotelPassThruCallback.of(passthruCallback()));

        assertEquals(ExotelCallStatus.COMPLETED, call.getExotelCallStatus(), "must leave IN_PROGRESS");
        assertEquals("sip:agentsipid001", call.getTo(), "TO is the endpoint that rang, not the one first tried");
        assertEquals(51L, call.getDuration(), "total duration includes ringing");
        assertEquals(50L, call.getConversationDuration(), "conversation duration is the answered leg only");
        assertNull(call.getEndTime(), "an epoch end time means 'not set', not 1970");
    }
}
