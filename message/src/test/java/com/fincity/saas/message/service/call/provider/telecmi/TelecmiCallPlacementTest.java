package com.fincity.saas.message.service.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import com.fincity.saas.message.dao.call.CallProviderAppDAO;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dao.call.provider.telecmi.TelecmiDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiCall;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.CallConnectionService;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbound calls through TeleCMI's click-to-call, stubbed at the HTTP layer.
 *
 * <p>The order is what these tests are about as much as the bodies: the row must exist before
 * TeleCMI is asked to ring anyone, because the agent's leg starts within a second of the request
 * and its webhooks need a row to land on. {@code events} records the writes and the request in the
 * order they happened.
 */
class TelecmiCallPlacementTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AGENT = "1001_1111112";
    private static final ULong USER = ULong.valueOf(7);
    private static final String CUSTOMER = "+919000000003";
    private static final String AGENT_VIRTUAL_NUMBER = "+918012345678";
    private static final String AGENT_MOBILE = "+919000000001";

    private static final String INITIATED = "{\"code\":200,\"msg\":\"Call initiated\",\"request_id\":\"REQ-1\"}";

    private final Deque<Object> answers = new ArrayDeque<>();
    private final List<String> events = new ArrayList<>();
    private final List<JsonNode> sent = new ArrayList<>();

    /** Each state the row was written in, copied at the moment of the write. */
    private final List<TelecmiCall> writes = new ArrayList<>();

    private ProviderUserEndpointDAO endpoints;
    private CallConnectionService connections;
    private TelecmiDAO dao;
    private TelecmiCall stored;
    private TelecmiCallService service;
    private Connection connection;

    private void answer(String json) {
        this.answers.add(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build());
    }

    private static TelecmiCall copy(TelecmiCall call) {
        return new TelecmiCall()
                .setProviderCallId(call.getProviderCallId())
                .setCallStatus(call.getCallStatus())
                .setOwnerService(call.getOwnerService())
                .setFrom(call.getFrom())
                .setEndTime(call.getEndTime())
                .setTelecmiCallResponse(call.getTelecmiCallResponse());
    }

    @BeforeEach
    void setUp() {

        WebClient telecmi = WebClient.builder()
                .baseUrl(TelecmiApiConfig.DEFAULT_REST_BASE)
                .exchangeFunction(request -> {
                    MockClientHttpRequest captured = new MockClientHttpRequest(request.method(), request.url());
                    return request.writeTo(captured, ExchangeStrategies.withDefaults())
                            .then(Mono.defer(captured::getBodyAsString))
                            .flatMap(body -> {
                                this.events.add("POST " + request.url().getPath());
                                try {
                                    this.sent.add(MAPPER.readTree(body));
                                } catch (Exception e) {
                                    throw new IllegalStateException(e);
                                }
                                Object next = this.answers.poll();
                                if (next == null) throw new AssertionError("Unexpected call to TeleCMI");
                                if (next instanceof Throwable failure) return Mono.error(failure);
                                return Mono.just((ClientResponse) next);
                            });
                })
                .build();

        WebClientConfig clients = mock(WebClientConfig.class);
        when(clients.createTelecmiWebClient(any())).thenReturn(Mono.just(telecmi));

        CallProviderAppDAO apps = mock(CallProviderAppDAO.class);
        when(apps.findByClient(any(), any(), any())).thenReturn(Mono.just(new CallProviderApp()));

        ProviderUserEndpoint sip = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT)
                .setVirtualNumber(AGENT_VIRTUAL_NUMBER);
        ProviderUserEndpoint mobile = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_PSTN_PHONE)
                .setProviderUserId(AGENT)
                .setEndpointValue(AGENT_MOBILE);

        this.endpoints = mock(ProviderUserEndpointDAO.class);
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(sip, mobile));
        when(this.endpoints.findEndpoint(any(), any(), any(), eq(ProviderUserEndpoint.ENDPOINT_PSTN_PHONE)))
                .thenReturn(Mono.just(mobile));

        // Every refusal surfaces as the GenericException its guard builds, message = the key.
        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        // The row as the database holds it; the two placement writes behave as their SQL does.
        this.dao = mock(TelecmiDAO.class);
        when(this.dao.create(any())).thenAnswer(invocation -> {
            TelecmiCall call = invocation.getArgument(0);
            this.events.add("create");
            this.writes.add(copy(call));
            call.setId(ULong.valueOf(1));
            this.stored = call;
            return Mono.just(call);
        });
        when(this.dao.recordPlacement(any(), any(), any())).thenAnswer(invocation -> {
            this.events.add("update");
            if (this.stored.getProviderCallId() == null) this.stored.setProviderCallId(invocation.getArgument(1));
            this.stored.setTelecmiCallResponse(invocation.getArgument(2));
            this.writes.add(copy(this.stored));
            return Mono.just(this.stored);
        });
        when(this.dao.markPlacementFailed(any(), any())).thenAnswer(invocation -> {
            this.events.add("update");
            if (this.stored.getCallStatus() == CallStatus.QUEUED) {
                this.stored.setCallStatus(CallStatus.FAILED);
                if (invocation.getArgument(1) != null) this.stored.setTelecmiCallResponse(invocation.getArgument(1));
            }
            this.writes.add(copy(this.stored));
            return Mono.just(this.stored);
        });
        TelecmiDAO dao = this.dao;

        this.connection = new Connection()
                .setConnectionType(ConnectionType.CALL)
                .setConnectionSubType(ConnectionSubType.TELECMI)
                .setConnectionDetails(new HashMap<>(Map.of(
                        TelecmiApiConfig.APP_ID, "1111112",
                        TelecmiApiConfig.SECRET, "s",
                        TelecmiApiConfig.CALLER_ID, "+918087654321")));
        this.connection.setName("calls");

        this.connections = mock(CallConnectionService.class);
        when(this.connections.getCoreDocument(any(), any(), any())).thenReturn(Mono.just(this.connection));

        this.service = new TelecmiCallService();
        this.service.setIntegrationsService(
                new TelecmiIntegrationsService(clients, apps, this.endpoints, null, messages));
        ReflectionTestUtils.setField(this.service, "dao", dao);
        ReflectionTestUtils.setField(this.service, "msgService", messages);
        ReflectionTestUtils.setField(
                this.service, "cacheService", mock(CacheService.class, invocation -> Mono.just(Boolean.TRUE)));
        ReflectionTestUtils.setField(this.service, "callConnectionService", this.connections);
        ReflectionTestUtils.setField(this.service, "defaultCallOwnerService", "entity-processor");
    }

    private TelecmiCall browserDial(String toNumber) {
        return this.service
                .browserDialInternal("app", "CLIENT", "calls", USER, toNumber)
                .block();
    }

    private CallRequest mobileRequest() {
        CallRequest request = new CallRequest().setToNumber(PhoneNumber.of(CUSTOMER));
        request.setUserId(USER);
        request.setConnectionName("calls");
        return request;
    }

    private GenericException failure(Mono<?> call) {
        return assertThrows(GenericException.class, call::block);
    }

    // -----------------------------------------------------------------------------------------
    // The browser dial (S2)
    // -----------------------------------------------------------------------------------------

    @Test
    void theRowIsWrittenBeforeTelecmiIsAskedToRing() {

        this.answer(INITIATED);

        this.browserDial(CUSTOMER);

        assertEquals(List.of("create", "POST " + TelecmiApiConfig.clickToCallUrl(), "update"), this.events);
        assertNull(this.writes.getFirst().getProviderCallId());
        assertEquals(CallStatus.QUEUED, this.writes.getFirst().getCallStatus());
    }

    @Test
    void aBrowserDialRingsTheSoftphoneAsTheAgentAndStoresTheRequestId() throws IOException {

        this.answer(INITIATED);

        TelecmiCall call = this.browserDial(CUSTOMER);

        assertEquals(
                MAPPER.readTree("{\"user_id\":\"" + AGENT + "\",\"secret\":\"s\",\"to\":919000000003,"
                        + "\"callerid\":918012345678,\"webrtc\":true,\"followme\":false,"
                        + "\"extra_params\":{\"callCode\":\"" + call.getCode() + "\"}}"),
                this.sent.getFirst());

        assertEquals("REQ-1", call.getProviderCallId());
        assertEquals(CallStatus.QUEUED, call.getCallStatus());
        assertEquals(USER, call.getUserId());
        assertEquals("calls", call.getConnectionName());
        assertEquals("entity-processor", call.getOwnerService());
        assertTrue(call.getIsOutbound());
        assertEquals(AGENT, call.getFrom());
        assertEquals(CUSTOMER, call.getTo());
        assertEquals(CUSTOMER, call.getCustomerPhoneNumber());
        assertEquals(AGENT_VIRTUAL_NUMBER, call.getCallerId());
        assertEquals("REQ-1", call.getTelecmiCallResponse().get("request_id"));
    }

    @Test
    void theStoredRequestNeverCarriesTheSecret() {

        this.answer(INITIATED);

        TelecmiCall call = this.browserDial(CUSTOMER);

        assertFalse(call.getTelecmiCallRequest().containsKey("secret"));
        assertEquals(AGENT, call.getTelecmiCallRequest().get("user_id"));
        assertEquals(
                Map.of(TelecmiApiConfig.EXTRA_CALL_CODE, call.getCode()),
                call.getTelecmiCallRequest().get("extra_params"));
    }

    // -----------------------------------------------------------------------------------------
    // The mobile-first call (S3)
    // -----------------------------------------------------------------------------------------

    @Test
    void aMobileCallRingsThroughFollowMeAndKeepsTheOwnerItWasGiven() throws IOException {

        this.answer(INITIATED);

        TelecmiCall call = this.service
                .makeCallInternal("app", "CLIENT", this.mobileRequest(), "some-service")
                .block();

        JsonNode body = this.sent.getFirst();
        assertEquals(AGENT, body.get("user_id").asText());
        assertFalse(body.get("webrtc").asBoolean());
        assertTrue(body.get("followme").asBoolean());
        assertEquals(919000000003L, body.get("to").asLong());

        assertEquals("REQ-1", call.getProviderCallId());
        assertEquals("some-service", call.getOwnerService());
        assertEquals(AGENT_MOBILE, call.getFrom());
    }

    @Test
    void aMobileCallWithoutAnOwnerGoesToTheDefaultOwner() {

        this.answer(INITIATED);

        TelecmiCall call = this.service
                .makeCallInternal("app", "CLIENT", this.mobileRequest(), null)
                .block();

        assertEquals("entity-processor", call.getOwnerService());
    }

    @Test
    void aMobileCallNeedsTheAgentAndRingsNoOneWithout() {

        CallRequest request = this.mobileRequest();
        request.setUserId(null);

        GenericException refused = this.failure(this.service.makeCallInternal("app", "CLIENT", request, null));

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertTrue(this.events.isEmpty());
    }

    // -----------------------------------------------------------------------------------------
    // Caller id
    // -----------------------------------------------------------------------------------------

    @Test
    void theConnectionsCallerIdCanBeAskedForOverTheAgents() {

        this.answer(INITIATED);

        CallRequest request = this.mobileRequest().setCallerId(PhoneNumber.of("+918087654321"));
        TelecmiCall call =
                this.service.makeCallInternal("app", "CLIENT", request, null).block();

        assertEquals(918087654321L, this.sent.getFirst().get("callerid").asLong());
        assertEquals("+918087654321", call.getCallerId());
    }

    @Test
    void theAgentsOwnNumberCanBeAskedForInAnotherForm() {

        this.answer(INITIATED);

        // The page may send it as TeleCMI writes it; it is the same number once parsed.
        CallRequest request = this.mobileRequest().setCallerId(PhoneNumber.of("918012345678"));
        this.service.makeCallInternal("app", "CLIENT", request, null).block();

        assertEquals(918012345678L, this.sent.getFirst().get("callerid").asLong());
    }

    @Test
    void aCallerIdThatIsNeitherTheAgentsNorTheConnectionsIsRefusedBeforeAnythingIsWritten() {

        CallRequest request = this.mobileRequest().setCallerId(PhoneNumber.of("+918011112222"));

        GenericException refused = this.failure(this.service.makeCallInternal("app", "CLIENT", request, null));

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.TELECMI_CALLER_ID_NOT_ALLOWED, refused.getMessage());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void aCallerIdAskedForWithNothingToCheckItAgainstIsRefused() {

        ProviderUserEndpoint bare = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT);
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(bare));
        this.connection.getConnectionDetails().remove(TelecmiApiConfig.CALLER_ID);

        CallRequest request = this.mobileRequest().setCallerId(PhoneNumber.of("+918011112222"));

        GenericException refused = this.failure(this.service.makeCallInternal("app", "CLIENT", request, null));

        assertEquals(MessageResourceService.TELECMI_CALLER_ID_NOT_ALLOWED, refused.getMessage());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void theCallerIdCheckComparesParsedNumbersAndNeverTails() {

        assertTrue(TelecmiIntegrationsService.isOneOf("+918012345678", null, "", "918012345678"));
        // Same last ten digits, different country: refused, and not because either fails to parse.
        assertTrue(PhoneUtil.parse("+18012345678") != null);
        assertFalse(TelecmiIntegrationsService.isOneOf("+18012345678", "+918012345678"));
        assertFalse(TelecmiIntegrationsService.isOneOf("not a number", "+918012345678"));
        assertFalse(TelecmiIntegrationsService.isOneOf("+918012345678"));
    }

    @Test
    void withoutAnAgentVirtualNumberTheConnectionsCallerIdIsShown() {

        ProviderUserEndpoint bare = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT);
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(bare));

        this.answer(INITIATED);
        TelecmiCall call = this.browserDial(CUSTOMER);

        assertEquals(918087654321L, this.sent.getFirst().get("callerid").asLong());
        assertEquals("+918087654321", call.getCallerId());
    }

    @Test
    void aCallerIdWrittenWithoutThePlusIsReadWithItsCountryCode() {

        // TeleCMI's own samples and dashboard write numbers this way, so a connection may too.
        ProviderUserEndpoint bare = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT);
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(bare));
        this.connection.getConnectionDetails().put(TelecmiApiConfig.CALLER_ID, "918087654321");

        this.answer(INITIATED);
        this.browserDial(CUSTOMER);

        assertEquals(918087654321L, this.sent.getFirst().get("callerid").asLong());
    }

    @Test
    void noCallerIdAnywhereIsRefusedBeforeAnythingIsWritten() {

        ProviderUserEndpoint bare = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT);
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(bare));
        this.connection.getConnectionDetails().remove(TelecmiApiConfig.CALLER_ID);

        GenericException refused =
                this.failure(this.service.browserDialInternal("app", "CLIENT", "calls", USER, CUSTOMER));

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertTrue(this.events.isEmpty());
    }

    // -----------------------------------------------------------------------------------------
    // Refusals before the row
    // -----------------------------------------------------------------------------------------

    @Test
    void anUnprovisionedAgentIsForbiddenAndNothingIsWritten() {

        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.empty());

        GenericException refused =
                this.failure(this.service.browserDialInternal("app", "CLIENT", "calls", USER, CUSTOMER));

        assertEquals(HttpStatus.FORBIDDEN.value(), refused.getStatusCode().value());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void aNumberThatWillNotParseIsRefusedAndNothingIsWritten() {

        GenericException refused = this.failure(this.service.browserDialInternal("app", "CLIENT", "calls", USER, "12"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void anExotelConnectionIsNotDialledThroughTelecmi() {

        this.connection.setConnectionSubType(ConnectionSubType.EXOTEL);

        GenericException refused =
                this.failure(this.service.browserDialInternal("app", "CLIENT", "calls", USER, CUSTOMER));

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertTrue(this.events.isEmpty());
    }

    // -----------------------------------------------------------------------------------------
    // Refusals after the row: it must end FAILED, never left QUEUED
    // -----------------------------------------------------------------------------------------

    @Test
    void aRefusedCallMarksTheRowFailedWithTelecmisAnswer() {

        this.answer("{\"code\":420,\"msg\":\"You are not allowed to make call\"}");

        GenericException refused =
                this.failure(this.service.browserDialInternal("app", "CLIENT", "calls", USER, CUSTOMER));

        assertEquals(HttpStatus.BAD_GATEWAY.value(), refused.getStatusCode().value());

        TelecmiCall last = this.writes.getLast();
        assertEquals(CallStatus.FAILED, last.getCallStatus());
        assertNull(last.getProviderCallId());
        assertEquals(420, last.getTelecmiCallResponse().get("code"));
        // A call TeleCMI never started has no end; one that rang anyway gets it from its webhooks.
        assertNull(last.getEndTime());
    }

    @Test
    void aSuccessWithoutARequestIdIsAFailure() {

        this.answer("{\"code\":200,\"msg\":\"Call initiated\"}");

        GenericException refused =
                this.failure(this.service.browserDialInternal("app", "CLIENT", "calls", USER, CUSTOMER));

        assertEquals(HttpStatus.BAD_GATEWAY.value(), refused.getStatusCode().value());
        assertEquals(CallStatus.FAILED, this.writes.getLast().getCallStatus());
    }

    @Test
    void noAnswerAtAllMarksTheRowFailedAndReportsTheError() {

        this.answers.add(new IllegalStateException("connection reset"));

        assertThrows(IllegalStateException.class, () -> this.service
                .browserDialInternal("app", "CLIENT", "calls", USER, CUSTOMER)
                .block());

        assertEquals(List.of("create", "POST " + TelecmiApiConfig.clickToCallUrl(), "update"), this.events);
        assertEquals(CallStatus.FAILED, this.writes.getLast().getCallStatus());
        assertNull(this.writes.getLast().getTelecmiCallResponse());
    }

    // -----------------------------------------------------------------------------------------
    // Placement writes only what it owns: the first webhooks may already be on the row
    // -----------------------------------------------------------------------------------------

    @Test
    void placementNeverRewritesTheWholeRow() {

        this.answer(INITIATED);
        this.browserDial(CUSTOMER);

        this.answer("{\"code\":420,\"msg\":\"You are not allowed to make call\"}");
        this.failure(this.service.browserDialInternal("app", "CLIENT", "calls", USER, CUSTOMER));

        org.mockito.Mockito.verify(this.dao, org.mockito.Mockito.never()).update(any());
    }

    @Test
    void aCallAWebhookShowedRingingIsNeverMarkedFailed() {

        // The agent's leg started before TeleCMI's answer to the dial was lost.
        this.answers.add(new IllegalStateException("read timed out"));
        org.mockito.Mockito.doAnswer(invocation -> {
                    TelecmiCall call = invocation.getArgument(0);
                    call.setId(ULong.valueOf(1));
                    this.stored = call.setCallStatus(CallStatus.ORIGINATE);
                    return Mono.just(call);
                })
                .when(this.dao)
                .create(any());

        assertThrows(IllegalStateException.class, () -> this.service
                .browserDialInternal("app", "CLIENT", "calls", USER, CUSTOMER)
                .block());

        assertEquals(CallStatus.ORIGINATE, this.stored.getCallStatus());
        assertNull(this.stored.getEndTime());
    }

    // -----------------------------------------------------------------------------------------
    // Not yet available
    // -----------------------------------------------------------------------------------------

    @Test
    void thePublicRouteWhichChecksNoDealIsRefused() {

        GenericException refused = this.failure(this.service.makeCall(
                MessageAccess.of("app", "CLIENT", Boolean.TRUE), this.mobileRequest(), this.connection));

        assertEquals(HttpStatus.NOT_IMPLEMENTED.value(), refused.getStatusCode().value());
        assertTrue(this.events.isEmpty());
    }
}
