package com.fincity.saas.message.dto.call.provider.telecmi;

import static com.fincity.saas.message.jooq.tables.MessageTelecmiCalls.MESSAGE_TELECMI_CALLS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.jooq.tables.records.MessageTelecmiCallsRecord;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The TeleCMI row and its jOOQ record map onto each other, field for field.
 *
 * <p>The DAO writes with {@code record.from(dto)} and reads with {@code record.into(dto)}, both by
 * name. A field whose name drifts from its column is silently null on one side, which on this
 * table would lose the key a webhook is matched on or the status it reports — so every column
 * that carries meaning is checked in both directions.
 */
class TelecmiCallMappingTest {

    private static TelecmiCall sample() {
        return new TelecmiCall()
                .setProviderCallId("request-id")
                .setConnectionName("calls")
                .setOwnerService("entity-processor")
                .setIsOutbound(Boolean.FALSE)
                .setFromDialCode(91)
                .setFrom("9000000001")
                .setCustomerPhoneNumber("9000000001")
                .setCallerId("9000000002")
                .setCallStatus(CallStatus.BUSY)
                .setLeg1Cmiuuid("agent-leg")
                .setLeg2Cmiuuid("customer-leg")
                .setLeg2Status("missed")
                .setLeg2HangupReason("sent_reject")
                .setStartTime(LocalDateTime.of(2026, 9, 24, 16, 59, 0))
                .setConversationDuration(0L)
                .setRecordingFile("recording.mp3")
                .setTelecmiHttpFlowRequest(Map.of("appid", "1111112"))
                .setLeg2Cdr(Map.of("status", "missed"));
    }

    @Test
    void everyMeaningfulFieldReachesItsColumn() {

        MessageTelecmiCallsRecord rec = new MessageTelecmiCallsRecord();
        rec.from(sample());

        assertEquals("request-id", rec.get(MESSAGE_TELECMI_CALLS.PROVIDER_CALL_ID));
        assertEquals("calls", rec.get(MESSAGE_TELECMI_CALLS.CONNECTION_NAME));
        assertEquals("entity-processor", rec.get(MESSAGE_TELECMI_CALLS.OWNER_SERVICE));
        assertEquals(Boolean.FALSE, rec.get(MESSAGE_TELECMI_CALLS.IS_OUTBOUND));
        assertEquals((short) 91, rec.get(MESSAGE_TELECMI_CALLS.FROM_DIAL_CODE));
        assertEquals(CallStatus.BUSY, rec.get(MESSAGE_TELECMI_CALLS.CALL_STATUS));
        assertEquals("agent-leg", rec.get(MESSAGE_TELECMI_CALLS.LEG1_CMIUUID));
        assertEquals("customer-leg", rec.get(MESSAGE_TELECMI_CALLS.LEG2_CMIUUID));
        assertEquals("missed", rec.get(MESSAGE_TELECMI_CALLS.LEG2_STATUS));
        assertEquals("sent_reject", rec.get(MESSAGE_TELECMI_CALLS.LEG2_HANGUP_REASON));
        assertEquals("recording.mp3", rec.get(MESSAGE_TELECMI_CALLS.RECORDING_FILE));
        assertEquals(Map.of("appid", "1111112"), rec.get(MESSAGE_TELECMI_CALLS.TELECMI_HTTP_FLOW_REQUEST));
        assertEquals(Map.of("status", "missed"), rec.get(MESSAGE_TELECMI_CALLS.LEG2_CDR));
    }

    @Test
    void aStoredRowReadsBackUnchanged() {

        MessageTelecmiCallsRecord rec = new MessageTelecmiCallsRecord();
        rec.from(sample());

        TelecmiCall read = rec.into(TelecmiCall.class);

        assertEquals("request-id", read.getProviderCallId());
        assertEquals("calls", read.getConnectionName());
        assertFalse(read.getIsOutbound());
        assertEquals(91, read.getFromDialCode());
        assertEquals(CallStatus.BUSY, read.getCallStatus());
        assertEquals("missed", read.getLeg2Status());
        assertEquals("sent_reject", read.getLeg2HangupReason());
        assertEquals(LocalDateTime.of(2026, 9, 24, 16, 59, 0), read.getStartTime());
        assertEquals(Map.of("status", "missed"), read.getLeg2Cdr());
    }

    @Test
    void aNewCallStartsQueuedAndOutbound() {

        // The row for a placed call is written before TeleCMI answers, so its defaults are what the
        // first webhook finds if it arrives before the update.
        TelecmiCall call = new TelecmiCall();

        assertEquals(CallStatus.QUEUED, call.getCallStatus());
        assertEquals(Boolean.TRUE, call.getIsOutbound());
    }

    @Test
    void theSeriesPointsAtTheTable() {
        assertEquals(MESSAGE_TELECMI_CALLS, MessageSeries.TELECMI_CALL.getTable());
        assertEquals(TelecmiCall.class, MessageSeries.TELECMI_CALL.getDtoClass());
    }
}
