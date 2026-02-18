package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.controller.policy.ClientPinPolicyController;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.testutil.TestWebSecurityConfig;
import com.fincity.security.service.policy.ClientPinPolicyService;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { ClientPinPolicyController.class, TestWebSecurityConfig.class })
class ClientPinPolicyControllerTest {

    private static final String BASE_PATH = "/api/security/clientPinPolicy";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientPinPolicyService clientPinPolicyService;

    @Test
    void getClientAppPolicy_validHeaders_returns200() {

        ClientPinPolicy policy = new ClientPinPolicy();
        policy.setId(ULong.valueOf(1));
        policy.setClientId(ULong.valueOf(10));
        policy.setAppId(ULong.valueOf(5));
        policy.setLength((short) 6);
        policy.setReLoginAfterInterval(120L);
        policy.setExpiryInDays((short) 30);
        policy.setExpiryWarnInDays((short) 25);
        policy.setPinHistoryCount((short) 3);
        policy.setNoFailedAttempts((short) 3);
        policy.setUserLockTime(15L);

        when(clientPinPolicyService.getClientAppPolicy(eq("TESTCLIENT"), eq("testApp")))
                .thenReturn(Mono.just(policy));

        webTestClient.get()
                .uri(BASE_PATH + "/codes/policy")
                .header("clientCode", "TESTCLIENT")
                .header("appCode", "testApp")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.length").isEqualTo(6)
                .jsonPath("$.noFailedAttempts").isEqualTo(3);

        verify(clientPinPolicyService).getClientAppPolicy(eq("TESTCLIENT"), eq("testApp"));
    }

    @Test
    void create_validPolicy_returns200() {

        ClientPinPolicy created = new ClientPinPolicy();
        created.setId(ULong.valueOf(1));
        created.setClientId(ULong.valueOf(10));
        created.setAppId(ULong.valueOf(5));
        created.setLength((short) 6);
        created.setReLoginAfterInterval(60L);
        created.setExpiryInDays((short) 45);
        created.setExpiryWarnInDays((short) 40);
        created.setPinHistoryCount((short) 5);
        created.setNoFailedAttempts((short) 5);
        created.setUserLockTime(30L);

        when(clientPinPolicyService.create(any(ClientPinPolicy.class)))
                .thenReturn(Mono.just(created));

        ClientPinPolicy requestBody = new ClientPinPolicy();
        requestBody.setLength((short) 6);
        requestBody.setReLoginAfterInterval(60L);
        requestBody.setExpiryInDays((short) 45);
        requestBody.setExpiryWarnInDays((short) 40);
        requestBody.setPinHistoryCount((short) 5);

        webTestClient.post()
                .uri(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.length").isEqualTo(6);

        verify(clientPinPolicyService).create(any(ClientPinPolicy.class));
    }

    @Test
    void delete_validId_returnsNoContent() {

        when(clientPinPolicyService.delete(eq(ULong.valueOf(1))))
                .thenReturn(Mono.just(1));

        webTestClient.delete()
                .uri(BASE_PATH + "/1")
                .exchange()
                .expectStatus().isNoContent();

        verify(clientPinPolicyService).delete(eq(ULong.valueOf(1)));
    }
}
