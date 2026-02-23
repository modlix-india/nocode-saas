package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fincity.security.dto.plansnbilling.Plan;
import com.fincity.security.dto.plansnbilling.PlanLimit;
import com.fincity.security.testutil.TestWebSecurityConfig;
import com.fincity.security.jooq.enums.SecurityPlanLimitName;
import com.fincity.security.jooq.enums.SecurityPlanLimitStatus;
import com.fincity.security.jooq.enums.SecurityPlanStatus;
import com.fincity.security.model.ClientPlanRequest;
import com.fincity.security.service.plansnbilling.PlanService;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { PlanController.class, TestWebSecurityConfig.class })
class PlanControllerTest {

    private static final String BASE_PATH = "/api/security/plans";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PlanService planService;

    private Plan samplePlan;

    @BeforeEach
    void setUp() {
        samplePlan = new Plan();
        samplePlan.setId(ULong.valueOf(1));
        samplePlan.setClientId(ULong.valueOf(10));
        samplePlan.setName("Starter Plan");
        samplePlan.setDescription("Basic starter plan");
        samplePlan.setPlanCode("STARTER");
        samplePlan.setStatus(SecurityPlanStatus.ACTIVE);
        samplePlan.setForRegistration(true);
        samplePlan.setOrderNumber(1);
        samplePlan.setDefaultPlan(false);
        samplePlan.setPrepaid(false);
    }

    // ==================== GET /api/security/plans/registration ====================

    @Nested
    @DisplayName("GET /api/security/plans/registration")
    class GetRegistrationPlansTests {

        @Test
        @DisplayName("Should return 200 with list of registration plans")
        void getRegistrationPlans_returnsPlans() {

            Plan plan2 = new Plan();
            plan2.setId(ULong.valueOf(2));
            plan2.setClientId(ULong.valueOf(10));
            plan2.setName("Pro Plan");
            plan2.setPlanCode("PRO");
            plan2.setStatus(SecurityPlanStatus.ACTIVE);
            plan2.setForRegistration(true);
            plan2.setOrderNumber(2);

            when(planService.readRegistrationPlans(eq(false)))
                    .thenReturn(Mono.just(List.of(samplePlan, plan2)));

            webTestClient.get()
                    .uri(BASE_PATH + "/registration")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(2)
                    .jsonPath("$[0].name").isEqualTo("Starter Plan")
                    .jsonPath("$[0].planCode").isEqualTo("STARTER")
                    .jsonPath("$[1].name").isEqualTo("Pro Plan");

            verify(planService).readRegistrationPlans(eq(false));
        }

        @Test
        @DisplayName("Should pass includeMultiAppPlans=true when query param is set")
        void getRegistrationPlans_includeMultiApp_passesTrue() {

            when(planService.readRegistrationPlans(eq(true)))
                    .thenReturn(Mono.just(List.of(samplePlan)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/registration")
                            .queryParam("includeMultiAppPlans", "true")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0].planCode").isEqualTo("STARTER");

            verify(planService).readRegistrationPlans(eq(true));
        }

        @Test
        @DisplayName("Should return 200 with empty list when no registration plans exist")
        void getRegistrationPlans_empty_returnsEmptyList() {

            when(planService.readRegistrationPlans(eq(false)))
                    .thenReturn(Mono.just(List.of()));

            webTestClient.get()
                    .uri(BASE_PATH + "/registration")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(0);

            verify(planService).readRegistrationPlans(eq(false));
        }
    }

    // ==================== GET /api/security/plans/internal/limits ====================

    @Nested
    @DisplayName("GET /api/security/plans/internal/limits")
    class GetLimitsTests {

        @Test
        @DisplayName("Should return 200 with list of plan limits for given appCode and clientCode")
        void getLimits_validParams_returnsLimits() {

            PlanLimit limit1 = new PlanLimit();
            limit1.setId(ULong.valueOf(1));
            limit1.setPlanId(ULong.valueOf(1));
            limit1.setName(SecurityPlanLimitName.PAGES);
            limit1.setLimit(50);
            limit1.setStatus(SecurityPlanLimitStatus.ACTIVE);

            PlanLimit limit2 = new PlanLimit();
            limit2.setId(ULong.valueOf(2));
            limit2.setPlanId(ULong.valueOf(1));
            limit2.setName(SecurityPlanLimitName.STORAGE);
            limit2.setLimit(1024);
            limit2.setStatus(SecurityPlanLimitStatus.ACTIVE);

            when(planService.readLimits(eq("appx"), eq("client1")))
                    .thenReturn(Mono.just(List.of(limit1, limit2)));

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/internal/limits")
                            .queryParam("appCode", "appx")
                            .queryParam("clientCode", "client1")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(2)
                    .jsonPath("$[0].name").isEqualTo("PAGES")
                    .jsonPath("$[0].limit").isEqualTo(50)
                    .jsonPath("$[1].name").isEqualTo("STORAGE")
                    .jsonPath("$[1].limit").isEqualTo(1024);

            verify(planService).readLimits(eq("appx"), eq("client1"));
        }

        @Test
        @DisplayName("Should return 400 when required query params are missing")
        void getLimits_missingParams_returns400() {

            webTestClient.get()
                    .uri(BASE_PATH + "/internal/limits")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    // ==================== POST /api/security/plans/addToClient ====================

    @Nested
    @DisplayName("POST /api/security/plans/addToClient")
    class AddToClientTests {

        @Test
        @DisplayName("Should return 200 with true when plan is successfully added to client")
        void addToClient_validRequest_returnsTrue() {

            ClientPlanRequest request = new ClientPlanRequest()
                    .setClientId(ULong.valueOf(10))
                    .setUrlClientCode("TESTCLIENT")
                    .setPlanId(ULong.valueOf(1))
                    .setCycleId(ULong.valueOf(5))
                    .setEndDate(LocalDateTime.of(2027, 2, 18, 0, 0));

            when(planService.addPlanAndCyCle(any(ClientPlanRequest.class)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.post()
                    .uri(BASE_PATH + "/addToClient")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);

            verify(planService).addPlanAndCyCle(any(ClientPlanRequest.class));
        }

        @Test
        @DisplayName("Should return 500 when service throws RuntimeException during add")
        void addToClient_serviceError_returns500() {

            ClientPlanRequest request = new ClientPlanRequest()
                    .setClientId(ULong.valueOf(10))
                    .setPlanId(ULong.valueOf(999));

            when(planService.addPlanAndCyCle(any(ClientPlanRequest.class)))
                    .thenReturn(Mono.error(new RuntimeException("Plan conflict")));

            webTestClient.post()
                    .uri(BASE_PATH + "/addToClient")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    // ==================== DELETE /api/security/plans/removeFromClient ====================

    @Nested
    @DisplayName("DELETE /api/security/plans/removeFromClient")
    class RemoveFromClientTests {

        @Test
        @DisplayName("Should return 200 with true when plan is successfully removed from client")
        void removeFromClient_validRequest_returnsTrue() {

            when(planService.removeClientFromPlan(eq(ULong.valueOf(10)), eq(ULong.valueOf(1))))
                    .thenReturn(Mono.just(Boolean.TRUE));

            webTestClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/removeFromClient")
                            .queryParam("clientId", "10")
                            .queryParam("planId", "1")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);

            verify(planService).removeClientFromPlan(eq(ULong.valueOf(10)), eq(ULong.valueOf(1)));
        }

        @Test
        @DisplayName("Should return 400 when required query params are missing")
        void removeFromClient_missingParams_returns400() {

            webTestClient.delete()
                    .uri(BASE_PATH + "/removeFromClient")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Should return 500 when service throws RuntimeException during removal")
        void removeFromClient_serviceError_returns500() {

            when(planService.removeClientFromPlan(eq(ULong.valueOf(10)), eq(ULong.valueOf(1))))
                    .thenReturn(Mono.error(new RuntimeException("Unexpected error")));

            webTestClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/removeFromClient")
                            .queryParam("clientId", "10")
                            .queryParam("planId", "1")
                            .build())
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }
}
