package com.fincity.saas.message.service.call.provider.exotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.configuration.call.exotel.ExotelIntegrationsApiConfig;
import com.fincity.saas.message.dao.call.CallProviderAppDAO;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.model.response.call.provider.exotel.integrations.ExotelOutboundCallResult;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.service.MessageResourceService;
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
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The browser token and browser dial, Exotel stubbed at the HTTP layer. Exotel's token endpoint takes only
 * {@code customer} or {@code app}, and answers anything else with the 400 it really returns, so an agent-scoped
 * request fails here as it does against Exotel.
 */
class ExotelBrowserTokenTest {

    private static final MessageAccess TENANT = MessageAccess.of("app", "CLIENT", Boolean.TRUE);
    private static final ULong AGENT = ULong.valueOf(7);
    private static final String AGENT_EMAIL = "agent@example.test";
    private static final String APP_TOKEN = "app-token";

    private final ObjectMapper mapper = new ObjectMapper();

    private final List<JsonNode> tokenRequests = new ArrayList<>();
    private final List<JsonNode> dialRequests = new ArrayList<>();
    private final List<String> dialTokens = new ArrayList<>();

    private Function<JsonNode, Mono<ClientResponse>> tokenAnswer;
    private Function<JsonNode, Mono<ClientResponse>> dialAnswer;

    private ProviderUserEndpointDAO endpoints;
    private ExotelIntegrationsService service;
    private Connection connection;

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private WebClient capturing(List<JsonNode> into, Function<JsonNode, Mono<ClientResponse>> answer) {
        return WebClient.builder()
                .baseUrl("https://integrations.example.test")
                .exchangeFunction(request -> this.bodyOf(request).flatMap(body -> {
                    into.add(body);
                    return answer.apply(body);
                }))
                .build();
    }

    private Mono<JsonNode> bodyOf(ClientRequest request) {
        MockClientHttpRequest captured = new MockClientHttpRequest(request.method(), request.url());
        return request.writeTo(captured, ExchangeStrategies.withDefaults())
                .then(Mono.defer(captured::getBodyAsString))
                .map(body -> {
                    try {
                        return this.mapper.readTree(body);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    @BeforeEach
    void setUp() {

        this.tokenAnswer = body -> {
            String entity = body.path("Entity").asText();
            if (!"customer".equals(entity) && !"app".equals(entity))
                return json(
                        HttpStatus.BAD_REQUEST,
                        "{\"Status\":\"Failed\",\"Code\":400,\"Error\":\"Id, Secret and Entity are mandatory, "
                                + "Entity must be one of customer, app\"}");
            return json(HttpStatus.OK, "{\"Status\":\"Success\",\"Code\":200,\"Data\":\"" + APP_TOKEN + "\"}");
        };
        this.dialAnswer = body -> json(
                HttpStatus.OK,
                "{\"Status\":\"Success\",\"Code\":200,\"RequestId\":\"req-1\",\"Data\":{\"CallSid\":\"sid-1\"}}");

        WebClient tokenClient = this.capturing(this.tokenRequests, body -> this.tokenAnswer.apply(body));
        WebClient dialClient = this.capturing(this.dialRequests, body -> this.dialAnswer.apply(body));

        WebClientConfig clients = mock(WebClientConfig.class);
        when(clients.createExotelIntegrationsWebClient(any())).thenReturn(Mono.just(tokenClient));
        when(clients.createExotelIntegrationsWebClient(any(), any(), any())).thenAnswer(invocation -> {
            this.dialTokens.add(invocation.getArgument(1));
            return Mono.just(dialClient);
        });

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ExotelIntegrationsApiConfig.CUSTOMER_ID, "customer-id");
        CallProviderAppDAO apps = mock(CallProviderAppDAO.class);
        when(apps.findByClient(any(), any(), any()))
                .thenReturn(Mono.just(new CallProviderApp()
                        .setProviderAppId("app-id")
                        .setProviderAppSecret("app-secret")
                        .setProviderMetadata(metadata)));

        ProviderUserEndpoint sip = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT_EMAIL)
                .setVirtualNumber("+918012345678");
        this.endpoints = mock(ProviderUserEndpointDAO.class);
        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(sip));

        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            Object[] args = invocation.getArguments();
            StringBuilder message = new StringBuilder(String.valueOf(args[1]));
            for (int i = 2; i < args.length; i++) message.append(" | ").append(args[i]);
            return Mono.error(error.apply(message.toString()));
        });

        this.connection = new Connection();
        this.connection.setName("exotelCalls");

        this.service = new ExotelIntegrationsService(clients, apps, this.endpoints, null, messages, this.mapper);
    }

    private void assertAppTokenRequested() {
        assertEquals(1, this.tokenRequests.size());
        JsonNode request = this.tokenRequests.get(0);
        assertEquals("app", request.path("Entity").asText());
        assertEquals("app-id", request.path("Id").asText());
        assertFalse(request.has("AppUserId"), "Exotel has no agent-scoped token");
    }

    @Test
    void theBrowserTokenIsTheAppTokenWithTheAgentsExotelUser() {

        BrowserCallToken token = this.service
                .generateBrowserToken(TENANT, this.connection, AGENT)
                .block();

        this.assertAppTokenRequested();
        assertEquals(APP_TOKEN, token.getToken());
        assertEquals(AGENT_EMAIL, token.getProviderUserId());
    }

    @Test
    void theBrowserDialUsesTheAppTokenAndNamesTheAgentInTheBody() {

        ExotelOutboundCallResult result = this.service
                .placeOutboundCall(TENANT, this.connection, AGENT, "919000000001")
                .block();

        this.assertAppTokenRequested();
        assertEquals(List.of(APP_TOKEN), this.dialTokens);

        JsonNode dial = this.dialRequests.get(0);
        assertEquals(AGENT_EMAIL, dial.path("user_id").asText());
        assertEquals("app-id", dial.path("app_id").asText());
        assertEquals("customer-id", dial.path("customer_id").asText());
        assertEquals("919000000001", dial.path("to").asText());
        assertEquals("sid-1", result.getCallSid());
    }

    @Test
    void anAgentNotSetUpForBrowserCallingGetsNoToken() {

        when(this.endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.empty());

        Mono<BrowserCallToken> token = this.service.generateBrowserToken(TENANT, this.connection, AGENT);

        GenericException error = assertInstanceOf(GenericException.class, assertThrows(Exception.class, token::block));
        assertEquals(HttpStatus.FORBIDDEN.value(), error.getStatusCode().value());
        assertTrue(this.tokenRequests.isEmpty(), "Exotel is never asked for a token");
    }

    private GenericException refused(Mono<?> call) {
        return assertInstanceOf(GenericException.class, assertThrows(Exception.class, call::block));
    }

    @Test
    void anExotelErrorOnTheTokenIsA502WithExotelsReason() {

        this.tokenAnswer = body -> json(
                HttpStatus.UNAUTHORIZED,
                "{\"Status\":\"Failed\",\"Code\":401,\"Error\":\"Account is not active\"}");

        GenericException error = this.refused(this.service.generateBrowserToken(TENANT, this.connection, AGENT));

        assertEquals(HttpStatus.BAD_GATEWAY.value(), error.getStatusCode().value());
        assertTrue(error.getMessage().startsWith(MessageResourceService.EXOTEL_REQUEST_FAILED));
        assertTrue(error.getMessage().endsWith("| Account is not active"), error.getMessage());
    }

    @Test
    void anExotelErrorOnTheDialIsA502WithExotelsReason() {

        this.dialAnswer = body -> json(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "{\"Status\":\"Failed\",\"Code\":500,\"Error\":\"10715: no device to originate from\"}");

        GenericException error =
                this.refused(this.service.placeOutboundCall(TENANT, this.connection, AGENT, "919000000001"));

        assertEquals(HttpStatus.BAD_GATEWAY.value(), error.getStatusCode().value());
        assertTrue(
                error.getMessage().endsWith("| outbound call | 10715: no device to originate from"), error.getMessage());
    }

    @Test
    void anErrorWithoutExotelsBodyNamesTheHttpStatus() {

        this.dialAnswer = body -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body("<html>Service Unavailable</html>")
                .build());

        GenericException error =
                this.refused(this.service.placeOutboundCall(TENANT, this.connection, AGENT, "919000000001"));

        assertEquals(HttpStatus.BAD_GATEWAY.value(), error.getStatusCode().value());
        assertTrue(error.getMessage().endsWith("| outbound call | HTTP 503"), error.getMessage());
    }

    @Test
    void aLongExotelReasonIsCapped() {

        String reason = "x".repeat(500);
        this.tokenAnswer = body -> json(
                HttpStatus.BAD_REQUEST, "{\"Status\":\"Failed\",\"Code\":400,\"Error\":\"" + reason + "\"}");

        GenericException error = this.refused(this.service.generateBrowserToken(TENANT, this.connection, AGENT));

        assertTrue(error.getMessage().endsWith("| " + "x".repeat(200)), error.getMessage());
    }
}
