package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.model.TransportPOJO;
import com.fincity.security.service.TransportService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { TransportController.class, TestWebSecurityConfig.class })
class TransportControllerTest {

    private static final String BASE_PATH = "/api/security/transports";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TransportService transportService;

    @Test
    void makeTransport_validAppCode_returns200WithTransportPOJO() {

        TransportPOJO transport = new TransportPOJO()
                .setAppCode("testApp")
                .setName("Test Application")
                .setUniqueTransportCode("abc-123")
                .setType("APP");

        when(transportService.makeTransport(eq("testApp")))
                .thenReturn(Mono.just(transport));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_PATH + "/makeTransport")
                        .queryParam("applicationCode", "testApp")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.appCode").isEqualTo("testApp")
                .jsonPath("$.name").isEqualTo("Test Application")
                .jsonPath("$.uniqueTransportCode").isEqualTo("abc-123")
                .jsonPath("$.type").isEqualTo("APP");

        verify(transportService).makeTransport(eq("testApp"));
    }

    @Test
    void makeTransport_serviceReturnsEmpty_returns200WithEmptyBody() {

        when(transportService.makeTransport(eq("unknownApp")))
                .thenReturn(Mono.empty());

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_PATH + "/makeTransport")
                        .queryParam("applicationCode", "unknownApp")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody().isEmpty();

        verify(transportService).makeTransport(eq("unknownApp"));
    }

    @Test
    void createAndApply_validPojo_returns200WithTrue() {

        TransportPOJO transport = new TransportPOJO()
                .setAppCode("testApp")
                .setName("Test Application")
                .setClientCode("CLIENT1");

        when(transportService.createAndApply(any(TransportPOJO.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        webTestClient.post()
                .uri(BASE_PATH + "/createAndApply")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transport)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);

        verify(transportService).createAndApply(any(TransportPOJO.class));
    }
}
