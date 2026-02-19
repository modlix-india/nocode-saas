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

import com.fincity.security.controller.policy.ClientOtpPolicyController;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.testutil.TestWebSecurityConfig;
import com.fincity.security.jooq.enums.SecurityClientOtpPolicyTargetType;
import com.fincity.security.service.policy.ClientOtpPolicyService;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { ClientOtpPolicyController.class, TestWebSecurityConfig.class })
class ClientOtpPolicyControllerTest {

    private static final String BASE_PATH = "/api/security/clientOtpPolicy";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientOtpPolicyService clientOtpPolicyService;

    @Test
    void getClientAppPolicy_validHeaders_returns200() {

        ClientOtpPolicy policy = new ClientOtpPolicy();
        policy.setId(ULong.valueOf(1));
        policy.setClientId(ULong.valueOf(10));
        policy.setAppId(ULong.valueOf(5));
        policy.setTargetType(SecurityClientOtpPolicyTargetType.EMAIL);
        policy.setLength((short) 6);
        policy.setNumeric(true);
        policy.setAlphanumeric(false);
        policy.setExpireInterval(5L);

        when(clientOtpPolicyService.getClientAppPolicy(eq("TESTCLIENT"), eq("testApp")))
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
                .jsonPath("$.numeric").isEqualTo(true);

        verify(clientOtpPolicyService).getClientAppPolicy(eq("TESTCLIENT"), eq("testApp"));
    }

    @Test
    void create_validPolicy_returns200() {

        ClientOtpPolicy created = new ClientOtpPolicy();
        created.setId(ULong.valueOf(1));
        created.setClientId(ULong.valueOf(10));
        created.setAppId(ULong.valueOf(5));
        created.setTargetType(SecurityClientOtpPolicyTargetType.EMAIL);
        created.setLength((short) 6);
        created.setNumeric(true);
        created.setAlphanumeric(false);
        created.setExpireInterval(5L);
        created.setNoFailedAttempts((short) 3);
        created.setUserLockTime(15L);

        when(clientOtpPolicyService.create(any(ClientOtpPolicy.class)))
                .thenReturn(Mono.just(created));

        ClientOtpPolicy requestBody = new ClientOtpPolicy();
        requestBody.setTargetType(SecurityClientOtpPolicyTargetType.EMAIL);
        requestBody.setLength((short) 6);
        requestBody.setNumeric(true);
        requestBody.setAlphanumeric(false);
        requestBody.setExpireInterval(5L);

        webTestClient.post()
                .uri(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.length").isEqualTo(6)
                .jsonPath("$.numeric").isEqualTo(true);

        verify(clientOtpPolicyService).create(any(ClientOtpPolicy.class));
    }

    @Test
    void delete_validId_returnsNoContent() {

        when(clientOtpPolicyService.delete(eq(ULong.valueOf(1))))
                .thenReturn(Mono.just(1));

        webTestClient.delete()
                .uri(BASE_PATH + "/1")
                .exchange()
                .expectStatus().isNoContent();

        verify(clientOtpPolicyService).delete(eq(ULong.valueOf(1)));
    }
}
