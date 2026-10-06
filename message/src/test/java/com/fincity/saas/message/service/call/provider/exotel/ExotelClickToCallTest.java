package com.fincity.saas.message.service.call.provider.exotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.call.exotel.ExotelIntegrationsApiConfig;
import com.fincity.saas.message.dao.call.provider.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.provider.exotel.ExotelCallRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import com.fincity.saas.message.service.call.CallConnectionService;
import com.fincity.saas.message.service.call.CallService;
import java.math.BigInteger;
import java.util.ArrayList;
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
import reactor.core.publisher.Mono;

/**
 * Exotel's click-to-call, placed for the CRM through {@code makeCallInternal}, stubbed at the HTTP
 * layer.
 *
 * <p>Two things used to go wrong on every such call. The agent's number was read from the access,
 * which on this route is the service's own and has no user, so the call failed with a null
 * dereference before Exotel was asked. And the status URL was always the tenant's app URL, set over
 * whatever the connection said, so a connection could not route its statuses anywhere else.
 */
class ExotelClickToCallTest {

    private static final ULong AGENT = ULong.valueOf(7);
    private static final String AGENT_PHONE = "+919000000001";
    private static final String CUSTOMER = "+919000000003";
    private static final String CALLER_ID = "08012345678";
    private static final String APP_URL = "https://app.example.test";
    private static final String CONNECTION_CALLBACK =
            "https://tunnel.example.test/app/CLIENT/page/api/message/call/callback/exotel";

    private final List<String> bodies = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<Call> callRows = new ArrayList<>();
    private final List<ULong> callRowUsers = new ArrayList<>();

    private ExotelCallService service;
    private Connection connection;
    private User agent;

    @BeforeEach
    void setUp() {

        WebClient exotel = WebClient.builder()
                .baseUrl("https://api.in.exotel.com/v1/Accounts/acct")
                .exchangeFunction(request -> {
                    MockClientHttpRequest captured = new MockClientHttpRequest(request.method(), request.url());
                    return request.writeTo(captured, ExchangeStrategies.withDefaults())
                            .then(Mono.defer(captured::getBodyAsString))
                            .map(body -> {
                                this.events.add("POST " + request.url().getPath());
                                this.bodies.add(body);
                                return ClientResponse.create(HttpStatus.OK)
                                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                        .body("{\"Call\":{\"Sid\":\"SID-1\"}}")
                                        .build();
                            });
                })
                .build();

        WebClientConfig clients = mock(WebClientConfig.class);
        when(clients.createExotelWebClient(any())).thenReturn(Mono.just(exotel));

        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        ExotelDAO dao = mock(ExotelDAO.class);
        when(dao.create(any(ExotelCall.class))).thenAnswer(invocation -> {
            ExotelCall call = invocation.getArgument(0);
            this.events.add("create");
            call.setId(ULong.valueOf(11));
            return Mono.just(call);
        });
        when(dao.update(any(ExotelCall.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        CallService calls = mock(CallService.class);
        when(calls.createInternal(any(MessageAccess.class), any(), any(Call.class)))
                .thenAnswer(invocation -> {
                    this.callRowUsers.add(invocation.getArgument(1));
                    this.callRows.add(invocation.getArgument(2));
                    return Mono.just(invocation.getArgument(2));
                });

        this.agent = new User().setId(BigInteger.valueOf(7)).setPhoneNumber(AGENT_PHONE);
        IFeignSecurityService security = mock(IFeignSecurityService.class);
        when(security.getUserInternal(eq(BigInteger.valueOf(7)), any())).thenAnswer(i -> Mono.justOrEmpty(this.agent));
        when(security.getAppUrl(eq("app"), any())).thenReturn(Mono.just(APP_URL));

        this.connection = new Connection()
                .setConnectionType(ConnectionType.CALL)
                .setConnectionSubType(ConnectionSubType.EXOTEL)
                .setConnectionDetails(new HashMap<>(Map.of(
                        "accountSid", "acct",
                        "apiKey", "key",
                        "apiToken", "token",
                        ExotelCallRequest.Fields.callerId, CALLER_ID)));
        this.connection.setName("exotelCalls");
        this.connection.setAppCode("app");

        CallConnectionService connections = mock(CallConnectionService.class);
        when(connections.getCoreDocument(any(), any(), any())).thenReturn(Mono.just(this.connection));

        this.service = new ExotelCallService();
        ReflectionTestUtils.setField(this.service, "dao", dao);
        ReflectionTestUtils.setField(this.service, "msgService", messages);
        ReflectionTestUtils.setField(this.service, "securityService", security);
        ReflectionTestUtils.setField(
                this.service, "cacheService", mock(CacheService.class, invocation -> Mono.just(Boolean.TRUE)));
        ReflectionTestUtils.setField(this.service, "callConnectionService", connections);
        ReflectionTestUtils.setField(this.service, "callService", calls);
        ReflectionTestUtils.setField(this.service, "webClientConfig", clients);
    }

    private PhoneNumber customer = PhoneNumber.of(CUSTOMER);

    private CallRequest request(ULong userId) {
        CallRequest request = new CallRequest().setToNumber(this.customer);
        request.setUserId(userId);
        request.setConnectionName("exotelCalls");
        return request;
    }

    private ExotelCall place(ULong userId) {
        return this.service
                .makeCallInternal("app", "CLIENT", this.request(userId), "entity-processor")
                .block();
    }

    private GenericException refused(ULong userId) {
        return assertThrows(GenericException.class, () -> this.place(userId));
    }

    @Test
    void theAgentsProfileNumberIsRungFirstAndTheCallIsTheirs() {

        ExotelCall placed = this.place(AGENT);

        assertEquals("SID-1", placed.getSid());
        assertEquals(AGENT, placed.getUserId());
        assertEquals(AGENT_PHONE, placed.getFrom());
        assertEquals("entity-processor", placed.getOwnerService());
        assertTrue(this.bodies.getFirst().contains(AGENT_PHONE), "From is the agent's number");
        assertEquals(List.of("POST /v1/Accounts/acct/Calls/connect.json", "create"), this.events);
    }

    @Test
    void theCallRowNamesTheAgentTheConnectionAndTheExotelRow() {

        this.place(AGENT);

        Call row = this.callRows.getFirst();
        assertEquals(AGENT, this.callRowUsers.getFirst());
        assertEquals("exotelCalls", row.getConnectionName());
        assertEquals(ULong.valueOf(11), row.getExotelCallId());
    }

    @Test
    void aRequestWithoutAnAgentIsRefusedBeforeExotelIsAsked() {

        GenericException refused = this.refused(null);

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.MISSING_CALL_PARAMETERS, refused.getMessage());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void anAgentWithoutANumberIsARequestErrorNotANullDereference() {

        this.agent.setPhoneNumber(null);

        GenericException refused = this.refused(AGENT);

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.MISSING_CALL_PARAMETERS, refused.getMessage());
        assertTrue(this.events.isEmpty(), "nothing is placed and nothing is written");
    }

    @Test
    void anAgentSecurityDoesNotKnowIsAnErrorNotAnEmptyAnswer() {

        // Security answers an unknown id with an empty 200; passed on, the route answered 200 with no
        // call and no error.
        this.agent = null;

        GenericException refused = this.refused(AGENT);

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.INVALID_USER_FOR_CLIENT, refused.getMessage());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void aCustomerNumberThatDidNotParseIsARequestErrorBeforeAnythingElse() {

        // The number arrives parsed, and parsing an unreadable one gives null.
        this.customer = null;

        GenericException refused = this.refused(AGENT);

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.MISSING_CALL_PARAMETERS, refused.getMessage());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void thePublicRouteRefusesAnUnparsedNumberTooRatherThanDereferencingIt() {

        this.customer = null;
        MessageAccess signedIn = MessageAccess.of("app", "CLIENT", AGENT, true);

        GenericException refused = assertThrows(
                GenericException.class,
                () -> this.service
                        .makeCall(signedIn, this.request(null), this.connection)
                        .block());

        assertEquals(MessageResourceService.MISSING_CALL_PARAMETERS, refused.getMessage());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void noCallerIdAnywhereIsARequestErrorNotACallExotelRefuses() {

        this.connection.getConnectionDetails().remove(ExotelCallRequest.Fields.callerId);

        GenericException refused = this.refused(AGENT);

        assertEquals(HttpStatus.BAD_REQUEST.value(), refused.getStatusCode().value());
        assertEquals(MessageResourceService.MISSING_CALL_PARAMETERS, refused.getMessage());
        assertTrue(this.events.isEmpty());
    }

    @Test
    void statusesGoToTheConnectionsCallbackUrl() {

        this.connection.getConnectionDetails().put(ExotelIntegrationsApiConfig.CALLBACK_URL, CONNECTION_CALLBACK);

        this.place(AGENT);

        assertTrue(this.bodies.getFirst().contains(CONNECTION_CALLBACK));
        assertFalse(this.bodies.getFirst().contains(APP_URL), "the app URL no longer overrides the connection");
    }

    @Test
    void anExplicitStatusCallbackWinsOverTheCallbackUrl() {

        String explicit = "https://status.example.test/app/CLIENT/page/api/message/call/callback/exotel";
        this.connection.getConnectionDetails().put(ExotelIntegrationsApiConfig.CALLBACK_URL, CONNECTION_CALLBACK);
        this.connection.getConnectionDetails().put(ExotelCallRequest.Fields.statusCallback, explicit);

        this.place(AGENT);

        assertTrue(this.bodies.getFirst().contains(explicit));
        assertFalse(this.bodies.getFirst().contains(CONNECTION_CALLBACK));
    }

    @Test
    void aConnectionThatStatesNoUrlKeepsTheAppUrlAsBefore() {

        this.place(AGENT);

        assertTrue(this.bodies.getFirst().contains(APP_URL + "/api/message/call/callback/exotel"));
    }

    @Test
    void theStatedUrlIsTakenVerbatimAndBlankMeansNone() {

        assertEquals(
                CONNECTION_CALLBACK,
                ExotelCallService.statusCallbackOf(
                        Map.of(ExotelIntegrationsApiConfig.CALLBACK_URL, "  " + CONNECTION_CALLBACK + " ")));
        assertNull(ExotelCallService.statusCallbackOf(Map.of(ExotelCallRequest.Fields.statusCallback, " ")));
        assertNull(ExotelCallService.statusCallbackOf(Map.of()));
        assertNull(ExotelCallService.statusCallbackOf(null));
    }
}
