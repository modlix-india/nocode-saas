package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.controller.appregistration.AppRegistrationIntegrationController;
import com.fincity.security.dto.AppRegistrationIntegration;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import com.fincity.security.service.appregistration.AppRegistrationIntegrationService;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { AppRegistrationIntegrationController.class, TestWebSecurityConfig.class })
class AppRegistrationIntegrationControllerTest {

    private static final String BASE_PATH = "/api/security/appRegIntegration";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AppRegistrationIntegrationService appRegistrationIntegrationService;

    @Test
    void create_validIntegration_returns200() {

        AppRegistrationIntegration integration = new AppRegistrationIntegration()
                .setClientId(ULong.valueOf(10))
                .setAppId(ULong.valueOf(5))
                .setPlatform(SecurityAppRegIntegrationPlatform.GOOGLE)
                .setIntgId("google-client-id")
                .setIntgSecret("google-secret")
                .setLoginUri("https://accounts.google.com/o/oauth2/v2/auth");
        integration.setId(ULong.valueOf(1));

        when(appRegistrationIntegrationService.create(any(AppRegistrationIntegration.class)))
                .thenReturn(Mono.just(integration));

        AppRegistrationIntegration requestBody = new AppRegistrationIntegration()
                .setClientId(ULong.valueOf(10))
                .setAppId(ULong.valueOf(5))
                .setPlatform(SecurityAppRegIntegrationPlatform.GOOGLE)
                .setIntgId("google-client-id")
                .setIntgSecret("google-secret")
                .setLoginUri("https://accounts.google.com/o/oauth2/v2/auth");

        webTestClient.post()
                .uri(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.intgId").isEqualTo("google-client-id")
                .jsonPath("$.platform").isEqualTo("GOOGLE");

        verify(appRegistrationIntegrationService).create(any(AppRegistrationIntegration.class));
    }

    @Test
    void read_validId_returns200() {

        AppRegistrationIntegration integration = new AppRegistrationIntegration()
                .setClientId(ULong.valueOf(10))
                .setAppId(ULong.valueOf(5))
                .setPlatform(SecurityAppRegIntegrationPlatform.GOOGLE)
                .setIntgId("google-client-id")
                .setLoginUri("https://accounts.google.com/o/oauth2/v2/auth");
        integration.setId(ULong.valueOf(1));

        when(appRegistrationIntegrationService.read(eq(ULong.valueOf(1))))
                .thenReturn(Mono.just(integration));

        webTestClient.get()
                .uri(BASE_PATH + "/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.intgId").isEqualTo("google-client-id")
                .jsonPath("$.platform").isEqualTo("GOOGLE");

        verify(appRegistrationIntegrationService).read(eq(ULong.valueOf(1)));
    }

    @Test
    void delete_validId_returns200() {

        when(appRegistrationIntegrationService.delete(eq(ULong.valueOf(1))))
                .thenReturn(Mono.just(1));

        webTestClient.delete()
                .uri(BASE_PATH + "/1")
                .exchange()
                .expectStatus().isNoContent();

        verify(appRegistrationIntegrationService).delete(eq(ULong.valueOf(1)));
    }
}
