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

import com.fincity.security.controller.policy.ClientPasswordPolicyController;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.testutil.TestWebSecurityConfig;
import com.fincity.security.service.policy.ClientPasswordPolicyService;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { ClientPasswordPolicyController.class, TestWebSecurityConfig.class })
class ClientPasswordPolicyControllerTest {

    private static final String BASE_PATH = "/api/security/clientPasswordPolicy";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientPasswordPolicyService clientPasswordPolicyService;

    @Test
    void getClientAppPolicy_validHeaders_returns200() {

        ClientPasswordPolicy policy = new ClientPasswordPolicy();
        policy.setId(ULong.valueOf(1));
        policy.setClientId(ULong.valueOf(10));
        policy.setAppId(ULong.valueOf(5));
        policy.setAtleastOneUppercase(true);
        policy.setAtleastOneLowercase(true);
        policy.setAtleastOneDigit(true);
        policy.setAtleastOneSpecialChar(true);
        policy.setSpacesAllowed(false);
        policy.setPassMinLength((short) 8);
        policy.setPassMaxLength((short) 20);
        policy.setNoFailedAttempts((short) 3);
        policy.setUserLockTime(15L);

        when(clientPasswordPolicyService.getClientAppPolicy(eq("TESTCLIENT"), eq("testApp")))
                .thenReturn(Mono.just(policy));

        webTestClient.get()
                .uri(BASE_PATH + "/codes/policy")
                .header("clientCode", "TESTCLIENT")
                .header("appCode", "testApp")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.atleastOneUppercase").isEqualTo(true)
                .jsonPath("$.atleastOneLowercase").isEqualTo(true)
                .jsonPath("$.atleastOneDigit").isEqualTo(true)
                .jsonPath("$.atleastOneSpecialChar").isEqualTo(true)
                .jsonPath("$.passMinLength").isEqualTo(8)
                .jsonPath("$.passMaxLength").isEqualTo(20);

        verify(clientPasswordPolicyService).getClientAppPolicy(eq("TESTCLIENT"), eq("testApp"));
    }

    @Test
    void create_validPolicy_returns200() {

        ClientPasswordPolicy created = new ClientPasswordPolicy();
        created.setId(ULong.valueOf(1));
        created.setClientId(ULong.valueOf(10));
        created.setAppId(ULong.valueOf(5));
        created.setAtleastOneUppercase(true);
        created.setAtleastOneLowercase(true);
        created.setAtleastOneDigit(true);
        created.setAtleastOneSpecialChar(false);
        created.setSpacesAllowed(false);
        created.setPassMinLength((short) 10);
        created.setPassMaxLength((short) 24);
        created.setPassExpiryInDays((short) 30);
        created.setPassExpiryWarnInDays((short) 25);
        created.setPassHistoryCount((short) 5);
        created.setNoFailedAttempts((short) 5);
        created.setUserLockTime(30L);

        when(clientPasswordPolicyService.create(any(ClientPasswordPolicy.class)))
                .thenReturn(Mono.just(created));

        ClientPasswordPolicy requestBody = new ClientPasswordPolicy();
        requestBody.setAtleastOneUppercase(true);
        requestBody.setAtleastOneLowercase(true);
        requestBody.setAtleastOneDigit(true);
        requestBody.setAtleastOneSpecialChar(false);
        requestBody.setSpacesAllowed(false);
        requestBody.setPassMinLength((short) 10);
        requestBody.setPassMaxLength((short) 24);

        webTestClient.post()
                .uri(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.atleastOneUppercase").isEqualTo(true)
                .jsonPath("$.passMinLength").isEqualTo(10)
                .jsonPath("$.passMaxLength").isEqualTo(24);

        verify(clientPasswordPolicyService).create(any(ClientPasswordPolicy.class));
    }

    @Test
    void delete_validId_returnsNoContent() {

        when(clientPasswordPolicyService.delete(eq(ULong.valueOf(1))))
                .thenReturn(Mono.just(1));

        webTestClient.delete()
                .uri(BASE_PATH + "/1")
                .exchange()
                .expectStatus().isNoContent();

        verify(clientPasswordPolicyService).delete(eq(ULong.valueOf(1)));
    }
}
