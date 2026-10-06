package com.fincity.saas.message.service.call.provider.telecmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dao.call.provider.telecmi.TelecmiDAO;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.dto.call.provider.telecmi.TelecmiCall;
import com.fincity.saas.message.enums.call.CallStatus;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.response.call.provider.telecmi.TelecmiFlowReply;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.CallConnectionService;
import com.fincity.saas.message.service.call.event.CallEventService;
import com.fincity.saas.message.util.PhoneUtil;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Answering TeleCMI's inbound HTTP flow: whom to ring, and the row every later webhook lands on.
 *
 * <p>The flow request is the form TeleCMI posted live (guide F10), identifiers replaced.
 */
class TelecmiInboundFlowTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AGENT = "1001_1111112";
    private static final ULong USER = ULong.valueOf(7);
    private static final Map<String, String> FLOW = Map.of(
            "from", "919000000003",
            "to", "918012345678",
            "cmiuuid", "CONV-1",
            "appid", "1111112");

    private ProviderUserEndpointDAO endpoints;
    private TelecmiDAO dao;
    private CallEventService events;
    private TelecmiCallService service;
    private Connection connection;
    private User agent;

    private static ProviderUserEndpoint sip() {
        return new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT)
                .setEndpointValue(AGENT);
    }

    private static ProviderUserEndpoint mobile(String number) {
        return new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_PSTN_PHONE)
                .setProviderUserId(AGENT)
                .setEndpointValue(number);
    }

    @BeforeEach
    void setUp() {

        this.endpoints = mock(ProviderUserEndpointDAO.class);
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any()))
                .thenReturn(Flux.just(sip(), mobile("+919000000001")));

        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        this.connection = new Connection()
                .setConnectionType(ConnectionType.CALL)
                .setConnectionSubType(ConnectionSubType.TELECMI)
                .setConnectionDetails(
                        new HashMap<>(Map.of(TelecmiApiConfig.APP_ID, "1111112", TelecmiApiConfig.SECRET, "s")));
        this.connection.setName("calls");

        CallConnectionService connections = mock(CallConnectionService.class);
        when(connections.getCoreDocument(any(), any(), any())).thenReturn(Mono.just(this.connection));

        this.agent = new User();
        this.agent.setId(BigInteger.valueOf(7));
        this.agent.setPhoneNumber("+919000000009");
        IFeignSecurityService security = mock(IFeignSecurityService.class);
        when(security.getUserInternal(any(), any())).thenAnswer(invocation -> Mono.justOrEmpty(this.agent));

        this.dao = mock(TelecmiDAO.class);
        when(this.dao.existsByUniqueField(any(), any())).thenReturn(Mono.just(Boolean.FALSE));
        when(this.dao.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        this.events = mock(CallEventService.class);
        when(this.events.sendIncomingCallEvent(any(), any(), any(), any())).thenReturn(Mono.empty());

        this.service = new TelecmiCallService();
        this.service.setIntegrationsService(new TelecmiIntegrationsService(null, null, this.endpoints, null, messages));
        ReflectionTestUtils.setField(this.service, "dao", this.dao);
        ReflectionTestUtils.setField(this.service, "msgService", messages);
        ReflectionTestUtils.setField(this.service, "securityService", security);
        ReflectionTestUtils.setField(
                this.service, "cacheService", mock(CacheService.class, invocation -> Mono.just(Boolean.TRUE)));
        ReflectionTestUtils.setField(this.service, "callConnectionService", connections);
        ReflectionTestUtils.setField(this.service, "callEventService", this.events);
        ReflectionTestUtils.setField(this.service, "defaultCallOwnerService", "entity-processor");
    }

    private IncomingCallRequest request(Map<String, String> flow) {
        IncomingCallRequest request = new IncomingCallRequest().setProviderIncomingRequest(flow);
        request.setUserId(USER);
        request.setConnectionName("calls");
        return request;
    }

    private TelecmiFlowReply answer(Map<String, String> flow) {
        return this.service.connectCall("app", "CLIENT", this.request(flow)).block();
    }

    private GenericException refused(Map<String, String> flow) {
        return assertThrows(GenericException.class, () -> this.answer(flow));
    }

    // -----------------------------------------------------------------------------------------
    // Whom to ring
    // -----------------------------------------------------------------------------------------

    @Test
    void aProvisionedAgentIsRungAsTheirTelecmiUserWithTheirMobileAsFollowMe() throws Exception {

        TelecmiFlowReply reply = this.answer(FLOW);

        assertEquals(
                MAPPER.readTree("{\"code\":200,\"loop\":1,\"followme\":true,\"hangup\":false,\"timeout\":30,"
                        + "\"result\":[{\"agent_id\":\"" + AGENT + "\",\"phone\":\"919000000001\"}]}"),
                MAPPER.valueToTree(reply));
    }

    @Test
    void anAgentWithNoMobileIsRungOnTheirSoftphoneOnly() {

        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(sip()));

        TelecmiFlowReply reply = this.answer(FLOW);

        assertFalse(reply.getFollowme());
        assertEquals(AGENT, reply.getResult().getFirst().getAgentId());
        assertNull(reply.getResult().getFirst().getPhone());
    }

    @Test
    void anAgentWithNoEndpointsIsRungOnTheirProfilePhoneAsExotelDoes() {

        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.empty());

        TelecmiFlowReply reply = this.answer(FLOW);

        assertTrue(reply.getFollowme());
        assertNull(reply.getResult().getFirst().getAgentId());
        assertEquals("919000000009", reply.getResult().getFirst().getPhone());
    }

    @Test
    void theProfilePhoneIsForAnAgentWithNoEndpointsNotOneToAddToTheirs() {

        TelecmiFlowReply.Target target =
                TelecmiIntegrationsService.flowTarget(List.of(sip()), PhoneUtil.parse("+919000000009"));

        assertEquals(AGENT, target.getAgentId());
        assertNull(target.getPhone());
    }

    @Test
    void anAgentWithNothingToRingIsRefusedAndNoRowIsWritten() {

        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.empty());
        this.agent.setPhoneNumber(null);

        GenericException refused = this.refused(FLOW);

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.AGENT_UNREACHABLE, refused.getMessage());
        verify(this.dao, never()).create(any());
    }

    @Test
    void anAgentSecurityDoesNotKnowIsRefusedNotAnsweredEmpty() {

        // Security answers an unknown id with an empty 200; passed on, that was an empty reply.
        this.agent = null;

        GenericException refused = this.refused(FLOW);

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.INVALID_USER_FOR_CLIENT, refused.getMessage());
        verify(this.dao, never()).create(any());
    }

    @Test
    void theRingTimeoutComesFromTheConnection() {

        this.connection.getConnectionDetails().put(TelecmiApiConfig.FLOW_TIMEOUT, "20");
        assertEquals(20, this.answer(FLOW).getTimeout());

        this.connection.getConnectionDetails().put(TelecmiApiConfig.FLOW_TIMEOUT, "soon");
        assertEquals(30, this.answer(FLOW).getTimeout());
    }

    // -----------------------------------------------------------------------------------------
    // The row, written once
    // -----------------------------------------------------------------------------------------

    @Test
    void theFirstRequestWritesTheInboundRowAndTellsTheAgentsPage() {

        this.answer(FLOW);

        ArgumentCaptor<TelecmiCall> created = ArgumentCaptor.forClass(TelecmiCall.class);
        verify(this.dao).create(created.capture());
        TelecmiCall call = created.getValue();

        assertEquals("CONV-1", call.getProviderCallId());
        assertFalse(call.getIsOutbound());
        assertEquals(USER, call.getUserId());
        assertEquals("calls", call.getConnectionName());
        assertEquals("entity-processor", call.getOwnerService());
        assertEquals(CallStatus.QUEUED, call.getCallStatus());
        assertEquals("+919000000003", call.getCustomerPhoneNumber());
        assertEquals("+919000000003", call.getFrom());
        assertEquals("+918012345678", call.getCallerId());
        assertEquals(FLOW, call.getTelecmiHttpFlowRequest());
        verify(this.events).sendIncomingCallEvent(eq("app"), eq("CLIENT"), eq(USER), any());
    }

    @Test
    void aRepeatedRequestIsAnsweredAgainAndWritesNothing() {

        when(this.dao.existsByUniqueField(any(), eq("CONV-1"))).thenReturn(Mono.just(Boolean.TRUE));

        TelecmiFlowReply reply = this.answer(FLOW);

        assertEquals(AGENT, reply.getResult().getFirst().getAgentId());
        verify(this.dao, never()).create(any());
        verify(this.events, never()).sendIncomingCallEvent(any(), any(), any(), any());
    }

    @Test
    void theCallIsTaggedTelecmiForTheServiceThatReceivesIt() {
        assertEquals(
                "TELECMI",
                MAPPER.valueToTree(new TelecmiCall()).get("callProvider").asText());
    }

    // -----------------------------------------------------------------------------------------
    // Refusals
    // -----------------------------------------------------------------------------------------

    @Test
    void aRequestNamingAnotherAppIsRefused() {

        Map<String, String> other = new HashMap<>(FLOW);
        other.put("appid", "9999999");

        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                this.refused(other).getStatusCode().value());
        verify(this.dao, never()).create(any());
    }

    @Test
    void aRequestWithoutAnAppIdIsRefused() {

        Map<String, String> none = new HashMap<>(FLOW);
        none.remove("appid");

        assertEquals(
                HttpStatus.UNAUTHORIZED.value(),
                this.refused(none).getStatusCode().value());
    }

    @Test
    void theAgentAndTheCallIdAreRequired() {

        IncomingCallRequest noAgent = this.request(FLOW);
        noAgent.setUserId(null);
        assertThrows(
                GenericException.class,
                () -> this.service.connectCall("app", "CLIENT", noAgent).block());

        Map<String, String> noCall = new HashMap<>(FLOW);
        noCall.remove("cmiuuid");
        assertEquals(
                HttpStatus.BAD_REQUEST.value(),
                this.refused(noCall).getStatusCode().value());
    }

    @Test
    void anExotelConnectionIsNotAnsweredAsTelecmi() {

        this.connection.setConnectionSubType(ConnectionSubType.EXOTEL);

        assertEquals(
                HttpStatus.BAD_REQUEST.value(),
                this.refused(FLOW).getStatusCode().value());
    }
}
