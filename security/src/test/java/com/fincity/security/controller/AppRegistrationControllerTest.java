package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.security.controller.appregistration.AppRegistrationController;
import com.fincity.security.dto.appregistration.AbstractAppRegistration;
import com.fincity.security.dto.appregistration.AppRegistrationAccess;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.service.appregistration.AppRegistrationServiceV2;
import com.fincity.security.testutil.TestWebSecurityConfig;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { AppRegistrationController.class, TestWebSecurityConfig.class })
class AppRegistrationControllerTest {

    private static final String BASE_PATH = "/api/security/applications/reg";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AppRegistrationServiceV2 appRegistrationServiceV2;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private ObjectMapper objectMapper;

    @Test
    void create_validAppAccessType_returns200() {

        AppRegistrationAccess access = new AppRegistrationAccess();
        access.setId(ULong.valueOf(1));
        access.setClientId(ULong.valueOf(10));
        access.setAppId(ULong.valueOf(5));
        access.setAllowAppId(ULong.valueOf(20));
        access.setBusinessType("COMMON");

        Map<String, Object> requestBody = Map.of(
                "clientId", "10",
                "appId", "5",
                "allowAppId", "20",
                "businessType", "COMMON");

        doReturn(access).when(objectMapper).convertValue(any(Map.class), eq(AppRegistrationAccess.class));

        when(appRegistrationServiceV2.create(
                eq(AppRegistrationObjectType.APPLICATION_ACCESS),
                eq("testApp"),
                any(AbstractAppRegistration.class)))
                .thenReturn(Mono.just(access));

        webTestClient.post()
                .uri(BASE_PATH + "/testApp/appAccess")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clientId").isEqualTo(10)
                .jsonPath("$.appId").isEqualTo(5)
                .jsonPath("$.businessType").isEqualTo("COMMON");

        verify(appRegistrationServiceV2).create(
                eq(AppRegistrationObjectType.APPLICATION_ACCESS),
                eq("testApp"),
                any(AbstractAppRegistration.class));
    }

    @Test
    void query_validRequest_returnsPageOfRegistrations() {

        AppRegistrationAccess access = new AppRegistrationAccess();
        access.setId(ULong.valueOf(1));
        access.setClientId(ULong.valueOf(10));
        access.setAppId(ULong.valueOf(5));
        access.setBusinessType("COMMON");

        Page<AbstractAppRegistration> page = new PageImpl<>(List.of(access),
                org.springframework.data.domain.PageRequest.of(0, 10), 1);

        when(appRegistrationServiceV2.get(
                eq(AppRegistrationObjectType.APPLICATION_ACCESS),
                eq("testApp"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)))
                .thenReturn(Mono.just(page));

        Map<String, Object> queryBody = Map.of(
                "page", 0,
                "size", 10);

        webTestClient.post()
                .uri(BASE_PATH + "/testApp/appAccess/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(queryBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content[0].businessType").isEqualTo("COMMON");
    }

    @Test
    void delete_validId_returns200WithTrue() {

        when(appRegistrationServiceV2.delete(
                eq(AppRegistrationObjectType.APPLICATION_ACCESS),
                eq(ULong.valueOf(42))))
                .thenReturn(Mono.just(Boolean.TRUE));

        webTestClient.delete()
                .uri(BASE_PATH + "/appAccess/42")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);

        verify(appRegistrationServiceV2).delete(
                eq(AppRegistrationObjectType.APPLICATION_ACCESS),
                eq(ULong.valueOf(42)));
    }

    @Test
    void create_invalidUrlPart_returns400BadRequest() {

        Map<String, Object> requestBody = Map.of(
                "clientId", "10",
                "businessType", "COMMON");

        webTestClient.post()
                .uri(BASE_PATH + "/testApp/invalidUrlPart")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
