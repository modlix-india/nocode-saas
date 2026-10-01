package com.fincity.saas.message.service.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import com.fincity.saas.message.dao.call.CallProviderAppDAO;
import com.fincity.saas.message.dao.call.provider.telecmi.TelecmiDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiCall;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiWebhookEffect;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiWebhookEffectTest;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.dispatch.CallEventDispatch;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.ICallRecordingService;
import com.fincity.saas.message.service.call.event.CallEventService;
import com.fincity.saas.message.service.dispatch.EventDispatcher;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

/**
 * A webhook from arrival to hand-over: proof of tenant, finding the call, and telling the owner once.
 *
 * <p>The row writes themselves are {@code TelecmiDAO.apply}'s, checked against MySQL separately; here
 * the DAO answers what the write changed, and the test checks what is done with that.
 */
class TelecmiWebhookProcessingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TOKEN = "the-webhook-token";
    private static final MessageAccess TENANT = MessageAccess.of("app", "CLIENT", Boolean.TRUE);
    private static final ULong ROW_ID = ULong.valueOf(1);

    private CallProviderAppDAO apps;
    private TelecmiDAO dao;
    private EventDispatcher dispatcher;
    private CallEventService events;
    private TelecmiCallService service;

    private TelecmiCall row;

    @BeforeEach
    void setUp() {

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH, TelecmiIntegrationsService.sha256Hex(TOKEN));

        this.apps = mock(CallProviderAppDAO.class);
        when(this.apps.findByClient("app", "CLIENT", "telecmi"))
                .thenReturn(Mono.just(
                        new CallProviderApp().setProviderAppId("1111112").setProviderMetadata(metadata)));
        when(this.apps.findByClient(eq("app"), eq("OTHER"), any())).thenReturn(Mono.empty());

        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        this.row = new TelecmiCall()
                .setProviderCallId("REQ-1")
                .setConnectionName("calls")
                .setOwnerService("entity-processor")
                .setCallStatus(CallStatus.ORIGINATE);
        this.row.setId(ROW_ID);
        this.row.setCode("CODE1");
        this.row.setAppCode("app").setClientCode("CLIENT");
        this.row.setUserId(ULong.valueOf(7));

        this.dao = mock(TelecmiDAO.class);
        when(this.dao.findByUniqueField("REQ-1")).thenReturn(Mono.just(this.row));
        when(this.dao.findByUniqueField(anyString()))
                .thenAnswer(
                        invocation -> "REQ-1".equals(invocation.getArgument(0)) ? Mono.just(this.row) : Mono.empty());
        when(this.dao.findByCode(any(), any(), any())).thenReturn(Mono.empty());
        when(this.dao.apply(any(), any())).thenReturn(Mono.just(new TelecmiDAO.Applied(false, false)));
        when(this.dao.readById(ROW_ID)).thenAnswer(invocation -> Mono.just(this.row));

        this.dispatcher = mock(EventDispatcher.class);
        when(this.dispatcher.enqueueAndDispatch(any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        this.events = mock(CallEventService.class);
        when(this.events.sendCallStatusEvent(any(), any(), any(), any())).thenReturn(Mono.empty());

        this.service = new TelecmiCallService();
        this.service.setIntegrationsService(new TelecmiIntegrationsService(null, this.apps, null, null, messages));
        this.service.setEventDispatcher(this.dispatcher);
        ReflectionTestUtils.setField(this.service, "dao", this.dao);
        ReflectionTestUtils.setField(this.service, "msgService", messages);
        ReflectionTestUtils.setField(
                this.service, "cacheService", mock(CacheService.class, invocation -> Mono.just(Boolean.TRUE)));
        ReflectionTestUtils.setField(this.service, "callEventService", this.events);
        ReflectionTestUtils.setField(this.service, "defaultCallOwnerService", "entity-processor");
    }

    private static JsonNode body(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private static JsonNode customerCdr() throws Exception {
        return body(TelecmiWebhookEffectTest.outboundCdr("b", "answered", "sent_bye", 9, true));
    }

    private TelecmiCall process(MessageAccess access, String token, JsonNode body) {
        return this.service.processWebhook(access, token, body).block();
    }

    private void refused(MessageAccess access, String token, JsonNode body) {
        GenericException refused = assertThrows(GenericException.class, () -> this.process(access, token, body));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), refused.getStatusCode().value());
        verify(this.dao, never()).apply(any(), any());
        verify(this.dispatcher, never()).enqueueAndDispatch(any(), any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------------------------
    // Proof of tenant, before anything in the body is used
    // -----------------------------------------------------------------------------------------

    @Test
    void noTokenIsRefused() throws Exception {
        this.refused(TENANT, null, customerCdr());
        verify(this.apps, never()).findByClient(any(), any(), any());
    }

    @Test
    void aWrongTokenIsRefused() throws Exception {
        this.refused(TENANT, "guessed", customerCdr());
    }

    @Test
    void aTenantThatNeverSetUpTelecmiIsRefused() throws Exception {
        this.refused(MessageAccess.of("app", "OTHER", Boolean.TRUE), TOKEN, customerCdr());
    }

    @Test
    void aValidTokenOnAnotherAppsWebhookIsRefused() throws Exception {
        this.refused(
                TENANT,
                TOKEN,
                body(TelecmiWebhookEffectTest.outboundCdr("b", "answered", "sent_bye", 9, true)
                        .replace("\"appid\":1111112", "\"appid\":9999999")));
    }

    @Test
    void theTokenIsComparedAgainstItsStoredHashOnly() {
        CallProviderApp app = new CallProviderApp()
                .setProviderMetadata(
                        Map.of(TelecmiApiConfig.META_WEBHOOK_TOKEN_HASH, TelecmiIntegrationsService.sha256Hex(TOKEN)));

        assertTrue(TelecmiIntegrationsService.tokenMatches(app, TOKEN));
        assertFalse(TelecmiIntegrationsService.tokenMatches(app, TelecmiIntegrationsService.sha256Hex(TOKEN)));
        assertFalse(TelecmiIntegrationsService.tokenMatches(new CallProviderApp(), TOKEN));
    }

    // -----------------------------------------------------------------------------------------
    // Finding the call
    // -----------------------------------------------------------------------------------------

    @Test
    void aWebhookForNoCallOfOursIsAnsweredAndIgnored() throws Exception {

        assertNull(this.process(
                TENANT,
                TOKEN,
                body(TelecmiWebhookEffectTest.outboundEvent("a", "started")
                        .replace("REQ-1", "REQ-X")
                        .replace("CODE1", "CODE-X"))));

        verify(this.dao, never()).apply(any(), any());
    }

    @Test
    void aCallOfAnotherTenantIsNeverWrittenFromThisTenantsToken() throws Exception {

        this.row.setClientCode("SOMEONE_ELSE");

        assertNull(this.process(TENANT, TOKEN, customerCdr()));
        verify(this.dao, never()).apply(any(), any());
    }

    @Test
    void aWebhookWhoseCallCodeDisagreesWithTheRowIsNotItsRow() throws Exception {

        assertNull(this.process(
                TENANT,
                TOKEN,
                body(TelecmiWebhookEffectTest.outboundEvent("a", "started").replace("CODE1", "CODE-X"))));
        verify(this.dao, never()).apply(any(), any());
    }

    @Test
    void aWebhookThatBeatTheResponseIsFoundByOurCodeAndStampsTheRequestId() throws Exception {

        this.row.setProviderCallId(null);
        when(this.dao.findByUniqueField(anyString())).thenReturn(Mono.empty());
        when(this.dao.findByCode("app", "CLIENT", "CODE1")).thenReturn(Mono.just(this.row));

        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundEvent("a", "started")));

        ArgumentCaptor<TelecmiWebhookEffect> effect = ArgumentCaptor.forClass(TelecmiWebhookEffect.class);
        verify(this.dao).apply(eq(ROW_ID), effect.capture());
        assertEquals("REQ-1", effect.getValue().providerCallId());
    }

    @Test
    void aRowAlreadyKeyedOnAnotherRequestIdIsNotTheWebhooksRow() throws Exception {

        this.row.setProviderCallId("REQ-OTHER");
        when(this.dao.findByUniqueField(anyString())).thenReturn(Mono.empty());
        when(this.dao.findByCode("app", "CLIENT", "CODE1")).thenReturn(Mono.just(this.row));

        assertNull(this.process(TENANT, TOKEN, customerCdr()));
        verify(this.dao, never()).apply(any(), any());
    }

    // -----------------------------------------------------------------------------------------
    // Telling the owner: once, when the call is decided
    // -----------------------------------------------------------------------------------------

    @Test
    void theWebhookThatDecidesTheCallHandsItOver() throws Exception {

        when(this.dao.apply(any(), any())).thenReturn(Mono.just(new TelecmiDAO.Applied(true, true)));
        this.row
                .setCallStatus(CallStatus.COMPLETE)
                .setRecordingFile("REC-1.mp3")
                .setConversationDuration(9L);

        this.process(TENANT, TOKEN, customerCdr());

        ArgumentCaptor<Object> dispatch = ArgumentCaptor.forClass(Object.class);
        verify(this.dispatcher)
                .enqueueAndDispatch(
                        any(),
                        eq("entity-processor"),
                        eq(DispatchEventType.CALL_STATUS),
                        eq("REQ-1"),
                        dispatch.capture());

        CallEventDispatch sent = (CallEventDispatch) dispatch.getValue();
        assertEquals("REQ-1", sent.getProviderCallId());
        assertEquals("telecmi", sent.getCallProvider());
        assertEquals("completed", sent.getCallStatus());
        assertEquals(CallStatus.COMPLETE, sent.getNormalizedCallStatus());
        assertEquals(9L, sent.getConversationDuration());
        assertEquals(ICallRecordingService.RECORDING_URI + "CODE1", sent.getRecordingUrl());
    }

    @Test
    void webhooksThatDecideNothingAreNotHandedOver() throws Exception {

        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundEvent("a", "started")));
        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundEvent("b", "hangup")));

        verify(this.dispatcher, never()).enqueueAndDispatch(any(), any(), any(), any(), any());
        verify(this.dao, times(2)).apply(any(), any());
    }

    @Test
    void aRecordingArrivingAfterTheOutcomeIsHandedOverAgain() throws Exception {

        when(this.dao.apply(any(), any())).thenReturn(Mono.just(new TelecmiDAO.Applied(false, true)));
        this.row.setCallStatus(CallStatus.COMPLETE).setRecordingFile("REC-1.mp3");

        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundCdr("a", "answered", "recv_bye", 18, true)));

        verify(this.dispatcher, times(1)).enqueueAndDispatch(any(), any(), any(), any(), any());
    }

    @Test
    void aRetriedDecidingCdrHandsOverEvenThoughItWritesNothing() throws Exception {

        // The first delivery wrote the outcome and then failed before the outbox; TeleCMI retries.
        when(this.dao.apply(any(), any())).thenReturn(Mono.just(new TelecmiDAO.Applied(false, false)));
        this.row.setCallStatus(CallStatus.COMPLETE);

        this.process(TENANT, TOKEN, customerCdr());

        verify(this.dispatcher, times(1)).enqueueAndDispatch(any(), any(), any(), eq("REQ-1"), any());
    }

    @Test
    void aHangupAfterTheOutcomeIsNotHandedOver() throws Exception {

        this.row.setCallStatus(CallStatus.COMPLETE);

        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundEvent("b", "hangup")));
        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundCdr("a", "answered", "recv_bye", 18, false)));

        verify(this.dispatcher, never()).enqueueAndDispatch(any(), any(), any(), any(), any());
    }

    @Test
    void aRecordingBeforeTheOutcomeWaitsForIt() throws Exception {

        when(this.dao.apply(any(), any())).thenReturn(Mono.just(new TelecmiDAO.Applied(false, true)));

        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundCdr("a", "answered", "recv_bye", 18, true)));

        verify(this.dispatcher, never()).enqueueAndDispatch(any(), any(), any(), any(), any());
    }

    @Test
    void theAgentsPageHearsEveryWebhookButOnlyWhenTheCallHasAnAgent() throws Exception {

        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundEvent("a", "started")));
        verify(this.events, times(1)).sendCallStatusEvent(eq("app"), eq("CLIENT"), eq(ULong.valueOf(7)), any());

        this.row.setUserId(null);
        this.process(TENANT, TOKEN, body(TelecmiWebhookEffectTest.outboundEvent("a", "answered")));
        verify(this.events, times(1)).sendCallStatusEvent(any(), any(), any(), any());
    }

    // -----------------------------------------------------------------------------------------
    // The words entity-processor already reads
    // -----------------------------------------------------------------------------------------

    @Test
    void everyOutcomeIsInWordsEntityProcessorParses() {

        assertEquals("completed", TelecmiCallService.ownerVocabulary(CallStatus.COMPLETE));
        assertEquals("busy", TelecmiCallService.ownerVocabulary(CallStatus.BUSY));
        assertEquals("no-answer", TelecmiCallService.ownerVocabulary(CallStatus.NO_ANSWER));
        assertEquals("in-progress", TelecmiCallService.ownerVocabulary(CallStatus.ORIGINATE));
        assertEquals("queued", TelecmiCallService.ownerVocabulary(CallStatus.QUEUED));
        assertEquals("failed", TelecmiCallService.ownerVocabulary(CallStatus.FAILED));
        assertNull(TelecmiCallService.ownerVocabulary(CallStatus.UNKNOWN));
    }

    @Test
    void answeredMeansLiveOnAnEventAndDoneOnACdr() {

        assertEquals("in-progress", TelecmiCallService.legVocabulary("answered", null, false));
        assertEquals("in-progress", TelecmiCallService.legVocabulary("started", null, false));
        assertNull(TelecmiCallService.legVocabulary("hangup", null, false));
        assertEquals("completed", TelecmiCallService.legVocabulary("answered", "sent_bye", true));
        assertEquals("busy", TelecmiCallService.legVocabulary("missed", "sent_reject", true));
        assertEquals("no-answer", TelecmiCallService.legVocabulary("missed", "recv_cancel", true));
    }
}
