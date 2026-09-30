package com.fincity.saas.message.dto.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.model.request.call.provider.telecmi.TelecmiWebhook;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * What each webhook of a call may change, over the bodies TeleCMI sent live (guide §5.1–§5.2),
 * identifiers replaced.
 */
public class TelecmiWebhookEffectTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDateTime ARRIVED = LocalDateTime.of(2026, 9, 28, 16, 17, 44);

    /** F6, leg a (the agent), any event. */
    public static String outboundEvent(String leg, String status) {
        return "{\"call_id\":\"C-" + leg + "\",\"leg\":\"" + leg + "\",\"type\":\"event\",\"user\":\"1001_1111112\","
                + "\"cmiuuid\":\"U-" + leg + "\",\"direction\":\"outbound\",\"callerid\":\"+919000000003\","
                + "\"app_id\":1111112,\"time\":1790246844496,\"custom\":\"none\","
                + "\"extra_params\":{\"callCode\":\"CODE1\"},\"request_id\":\"REQ-1\","
                + "\"status\":\"" + status + "\",\"to\":\"919000000001\",\"conversation_uuid\":\"U-a\"}";
    }

    public static String outboundCdr(String leg, String status, String reason, Integer answered, boolean recorded) {
        return "{\"virtual_number\":918012345678,\"call_id\":\"CDR-" + leg + "\",\"custom\":\"false\",\"leg\":\"" + leg
                + "\",\"type\":\"cdr\",\"appid\":1111112,\"to\":919000000003,\"cmiuuid\":\"U-" + leg + "\","
                + "\"status\":\"" + status + "\",\"user\":\"1001_1111112\",\"time\":1790246844496,"
                + "\"direction\":\"outbound\"," + (answered == null ? "" : "\"answeredsec\":" + answered + ",")
                + "\"hangup_reason\":\"" + reason + "\",\"request_id\":\"REQ-1\","
                + "\"extra_params\":{\"callCode\":\"CODE1\"}"
                + (recorded ? ",\"record\":true,\"filename\":\"REC-1.mp3\"" : "") + "}";
    }

    private static TelecmiWebhookEffect effect(TelecmiCall call, String json) throws Exception {
        return TelecmiWebhookEffect.of(call, TelecmiWebhook.of(MAPPER.readTree(json)), ARRIVED);
    }

    private static TelecmiCall outbound() {
        return new TelecmiCall().setIsOutbound(Boolean.TRUE);
    }

    private static TelecmiCall inbound() {
        return new TelecmiCall().setIsOutbound(Boolean.FALSE);
    }

    // -----------------------------------------------------------------------------------------
    // A call we placed: leg a is the agent, leg b the customer
    // -----------------------------------------------------------------------------------------

    @Test
    void anAgentLegEventProgressesTheCallAndTouchesOnlyLegOne() throws Exception {

        TelecmiWebhookEffect started = effect(outbound(), outboundEvent("a", "started"));

        assertEquals(TelecmiWebhookEffect.AGENT_LEG, started.leg());
        assertEquals("U-a", started.cmiuuid());
        assertEquals("started", started.legStatus());
        assertTrue(started.progressed());
        assertFalse(started.cdr());
        assertNull(started.cdrBody());
        assertNull(started.finalStatus());
        assertNull(started.endTime());
        assertEquals("REQ-1", started.providerCallId());
        assertNotNull(started.startTime());
    }

    @Test
    void aCustomerHangupEndsTheCallButDecidesNothing() throws Exception {

        TelecmiWebhookEffect hangup = effect(outbound(), outboundEvent("b", "hangup"));

        assertEquals(TelecmiWebhookEffect.CUSTOMER_LEG, hangup.leg());
        assertEquals(ARRIVED, hangup.endTime());
        assertFalse(hangup.progressed());
        assertNull(hangup.finalStatus());
    }

    @Test
    void anAgentHangupDoesNotEndTheCall() throws Exception {
        assertNull(effect(outbound(), outboundEvent("a", "hangup")).endTime());
    }

    @Test
    void theCustomerLegsAnsweredCdrDecidesTheCallWithItsTalkTimeAndRecording() throws Exception {

        TelecmiWebhookEffect cdr = effect(outbound(), outboundCdr("b", "answered", "sent_bye", 9, true));

        assertEquals(TelecmiWebhookEffect.CUSTOMER_LEG, cdr.leg());
        assertTrue(cdr.cdr());
        assertEquals(CallStatus.COMPLETE, cdr.finalStatus());
        assertFalse(cdr.onlyIfCustomerNeverRang());
        assertEquals(9L, cdr.conversationDuration());
        assertEquals("REC-1.mp3", cdr.recordingFile());
        assertEquals("sent_bye", cdr.hangupReason());
        assertEquals(ARRIVED, cdr.endTime());
        assertEquals("REQ-1", cdr.cdrBody().get("request_id"));
    }

    @Test
    void theAgentLegsCdrDecidesNothingWhenItWasAnswered() throws Exception {

        // Leg a says answered even when the customer never did (F9), so it can decide nothing.
        TelecmiWebhookEffect cdr = effect(outbound(), outboundCdr("a", "answered", "recv_bye", 18, false));

        assertEquals(TelecmiWebhookEffect.AGENT_LEG, cdr.leg());
        assertNull(cdr.finalStatus());
        assertNull(cdr.conversationDuration());
        assertNull(cdr.endTime());
    }

    @Test
    void anAgentWhoNeverAnsweredEndsTheCallOnlyIfTheCustomerNeverRang() throws Exception {

        TelecmiWebhookEffect cdr = effect(outbound(), outboundCdr("a", "missed", "recv_cancel", null, false));

        assertEquals(CallStatus.NO_ANSWER, cdr.finalStatus());
        assertTrue(cdr.onlyIfCustomerNeverRang());
        assertEquals(ARRIVED, cdr.endTime());
    }

    @Test
    void theCustomerLegsMissedCdrMapsTheHangupReason() throws Exception {

        assertEquals(
                CallStatus.BUSY,
                effect(outbound(), outboundCdr("b", "missed", "sent_reject", null, false))
                        .finalStatus());
        assertEquals(
                CallStatus.NO_ANSWER,
                effect(outbound(), outboundCdr("b", "missed", "recv_cancel", null, false))
                        .finalStatus());
        // answeredsec absent on a missed CDR means no talk time, not an unknown one.
        assertEquals(
                0L,
                effect(outbound(), outboundCdr("b", "missed", "recv_cancel", null, false))
                        .conversationDuration());
    }

    @Test
    void anUnrecognisedCdrStatusIsStillAnOutcome() throws Exception {
        assertEquals(
                CallStatus.UNKNOWN,
                effect(outbound(), outboundCdr("b", "voicemail", "x", null, false))
                        .finalStatus());
        assertTrue(TelecmiWebhookEffect.FINAL.contains(CallStatus.UNKNOWN));
    }

    @Test
    void failedIsNotFinalSoACallThatRangAnywayCanStillReport() {
        assertFalse(TelecmiWebhookEffect.FINAL.contains(CallStatus.FAILED));
    }

    @Test
    void aWebhookWithNoLegOnAPlacedCallTouchesNoLeg() throws Exception {

        TelecmiWebhookEffect none =
                effect(outbound(), "{\"type\":\"event\",\"status\":\"started\",\"request_id\":\"REQ-1\"}");

        assertEquals(TelecmiWebhookEffect.NO_LEG, none.leg());
        assertNull(none.legStatus());
    }

    // -----------------------------------------------------------------------------------------
    // An inbound call: the caller's leg has no leg field, the agent's is "b"
    // -----------------------------------------------------------------------------------------

    @Test
    void onAnInboundCallTheCallersLegIsTheCustomerAndTelecmisBIsTheAgent() throws Exception {

        TelecmiWebhookEffect caller = effect(
                inbound(),
                "{\"type\":\"event\",\"direction\":\"inbound\",\"conversation_uuid\":\"CONV-1\",\"cmiuuid\":\"CONV-1\","
                        + "\"from\":\"919000000003\",\"app_id\":1111112,\"time\":1790249394912,\"status\":\"answered\"}");
        TelecmiWebhookEffect agent = effect(
                inbound(),
                "{\"leg\":\"b\",\"type\":\"event\",\"conversation_uuid\":\"CONV-1\",\"cmiuuid\":\"U-b\","
                        + "\"direction\":\"outbound\",\"app_id\":1111112,\"time\":1790249395701,\"status\":\"started\"}");

        assertEquals(TelecmiWebhookEffect.CUSTOMER_LEG, caller.leg());
        assertEquals(TelecmiWebhookEffect.AGENT_LEG, agent.leg());
        assertNull(caller.providerCallId());
    }

    @Test
    void anInboundCallsSingleCdrDecidesIt() throws Exception {

        TelecmiWebhookEffect cdr = effect(
                inbound(),
                "{\"virtual_number\":918012345678,\"appid\":1111112,\"type\":\"cdr\",\"direction\":\"inbound\","
                        + "\"cmiuuid\":\"U-b\",\"status\":\"answered\",\"time\":1790249395701,\"answeredsec\":12,"
                        + "\"hangup_reason\":\"sent_bye\",\"record\":true,\"filename\":\"REC-2.mp3\","
                        + "\"conversation_uuid\":\"CONV-1\"}");

        assertEquals(TelecmiWebhookEffect.CUSTOMER_LEG, cdr.leg());
        assertEquals(CallStatus.COMPLETE, cdr.finalStatus());
        assertEquals(12L, cdr.conversationDuration());
        assertEquals("REC-2.mp3", cdr.recordingFile());
    }

    @Test
    void anInboundCustomerLegIsAlwaysNamedByTheConversationNotTheAgentsLegOnTheCdr() throws Exception {

        String cdr = "{\"appid\":1111112,\"type\":\"cdr\",\"direction\":\"inbound\",\"cmiuuid\":\"U-agent\","
                + "\"status\":\"answered\",\"answeredsec\":12,\"conversation_uuid\":\"CONV-1\"}";
        String callerEvent = "{\"type\":\"event\",\"direction\":\"inbound\",\"cmiuuid\":\"CONV-1\","
                + "\"conversation_uuid\":\"CONV-1\",\"status\":\"answered\"}";

        assertEquals("CONV-1", effect(inbound(), cdr).cmiuuid());
        assertEquals("CONV-1", effect(inbound(), callerEvent).cmiuuid());
    }

    @Test
    void theAgentLegAndPlacedCallsKeepTheirOwnLegId() throws Exception {
        assertEquals(
                "U-b",
                effect(
                                inbound(),
                                "{\"leg\":\"b\",\"type\":\"event\",\"cmiuuid\":\"U-b\",\"conversation_uuid\":\"CONV-1\","
                                        + "\"status\":\"started\"}")
                        .cmiuuid());
        // A placed call's webhooks all carry conversation_uuid = leg a's id; leg b must keep its own.
        assertEquals("U-b", effect(outbound(), outboundEvent("b", "started")).cmiuuid());
    }
}
