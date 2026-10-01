package com.fincity.saas.message.service.call.provider.exotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.configuration.WebClientConfig;
import com.fincity.saas.message.dao.call.CallProviderAppDAO;
import com.fincity.saas.message.dao.call.ProviderUserEndpointDAO;
import com.fincity.saas.message.dto.call.CallProviderApp;
import com.fincity.saas.message.dto.call.ProviderUserEndpoint;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.service.MessageResourceService;
import java.util.function.Function;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The agent's browser status checked with Exotel, Exotel stubbed at the HTTP layer.
 *
 * <p>A check Exotel could not answer must say so: the agent keeps the status our rows give, unchecked,
 * rather than being reported as checked and not provisioned.
 */
class ExotelBrowserStatusTest {

    private static final MessageAccess TENANT = MessageAccess.of("app", "CLIENT", Boolean.TRUE);
    private static final ULong AGENT = ULong.valueOf(7);
    private static final String AGENT_EMAIL = "agent@example.test";

    private static final String TOKEN = "{\"Status\":\"Success\",\"Code\":200,\"Data\":\"app-token\"}";

    private Function<ClientRequest, Mono<ClientResponse>> tokenAnswer;
    private Function<ClientRequest, Mono<ClientResponse>> mappingAnswer;

    private ExotelIntegrationsService service;
    private Connection connection;

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private static String mapping(boolean active, String sipId) {
        return "{\"Status\":\"Success\",\"Code\":200,\"Data\":[{\"AppUserId\":\"" + AGENT_EMAIL + "\",\"IsActive\":"
                + active + (sipId == null ? "" : ",\"SipId\":\"" + sipId + "\"") + "}]}";
    }

    @BeforeEach
    void setUp() {

        WebClient tokenClient = WebClient.builder()
                .baseUrl("https://integrations.example.test")
                .exchangeFunction(request -> this.tokenAnswer.apply(request))
                .build();
        WebClient appClient = WebClient.builder()
                .baseUrl("https://integrations.example.test")
                .exchangeFunction(request -> this.mappingAnswer.apply(request))
                .build();

        WebClientConfig clients = mock(WebClientConfig.class);
        when(clients.createExotelIntegrationsWebClient(any())).thenReturn(Mono.just(tokenClient));
        when(clients.createExotelIntegrationsWebClient(any(), any(), any())).thenReturn(Mono.just(appClient));

        CallProviderAppDAO apps = mock(CallProviderAppDAO.class);
        when(apps.findByClient(any(), any(), any()))
                .thenReturn(Mono.just(
                        new CallProviderApp().setProviderAppId("app-id").setProviderAppSecret("app-secret")));

        ProviderUserEndpoint sip = new ProviderUserEndpoint()
                .setEndpointType(ProviderUserEndpoint.ENDPOINT_WEBRTC_SIP)
                .setProviderUserId(AGENT_EMAIL)
                .setVirtualNumber("+918012345678");
        ProviderUserEndpointDAO endpoints = mock(ProviderUserEndpointDAO.class);
        when(endpoints.findActiveEndpoints(any(), any(), any(), any())).thenReturn(Flux.just(sip));

        MessageResourceService messages = mock(MessageResourceService.class, invocation -> {
            if (!"throwMessage".equals(invocation.getMethod().getName())) return null;
            @SuppressWarnings("unchecked")
            Function<String, GenericException> error = invocation.getArgument(0);
            return Mono.error(error.apply(invocation.getArgument(1)));
        });

        this.connection = new Connection();
        this.connection.setName("exotelCalls");

        this.service = new ExotelIntegrationsService(clients, apps, endpoints, null, messages, new ObjectMapper());

        this.tokenAnswer = request -> json(HttpStatus.OK, TOKEN);
        this.mappingAnswer = request -> json(HttpStatus.OK, mapping(true, "sip:agentsipid001"));
    }

    private BrowserCallStatus checked() {
        return this.service
                .browserCallStatus(TENANT, this.connection, AGENT, true)
                .block();
    }

    @Test
    void anAgentExotelCannotBeCheckedForKeepsTheUncheckedStatus() {

        this.tokenAnswer = request -> Mono.error(new IllegalStateException("Exotel unreachable"));

        BrowserCallStatus status = this.checked();

        assertTrue(status.isProvisioned(), "our rows say provisioned; the failed check changes nothing");
        assertFalse(status.isDialReadyChecked(), "not reported as checked");
    }

    @Test
    void aMappingReadFailureAlsoKeepsTheUncheckedStatus() {

        this.mappingAnswer = request -> json(HttpStatus.INTERNAL_SERVER_ERROR, "{}");

        BrowserCallStatus status = this.checked();

        assertTrue(status.isProvisioned());
        assertFalse(status.isDialReadyChecked());
    }

    @Test
    void anActiveMappingWithASipIdIsProvisionedAndChecked() {

        BrowserCallStatus status = this.checked();

        assertTrue(status.isProvisioned());
        assertTrue(status.isDialReadyChecked());
    }

    @Test
    void anInactiveMappingIsCheckedAndNotProvisioned() {

        this.mappingAnswer = request -> json(HttpStatus.OK, mapping(false, "sip:agentsipid001"));

        BrowserCallStatus status = this.checked();

        assertFalse(status.isProvisioned());
        assertTrue(status.isDialReadyChecked());
    }

    @Test
    void noMappingAtExotelIsCheckedAndNotProvisioned() {

        // Our endpoint row outlived the mapping at Exotel.
        this.mappingAnswer = request -> json(HttpStatus.NOT_FOUND, "{}");

        BrowserCallStatus status = this.checked();

        assertFalse(status.isProvisioned());
        assertTrue(status.isDialReadyChecked());
        assertEquals("exotel", status.getProvider());
    }

    @Test
    void theCheapStatusNeverAsksExotel() {

        this.tokenAnswer = request -> {
            throw new AssertionError("Exotel must not be asked");
        };

        BrowserCallStatus status = this.service
                .browserCallStatus(TENANT, this.connection, AGENT, false)
                .block();

        assertTrue(status.isProvisioned());
        assertFalse(status.isDialReadyChecked());
    }
}
