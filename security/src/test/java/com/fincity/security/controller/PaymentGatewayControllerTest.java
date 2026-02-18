package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

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

import com.fincity.security.dto.invoicesnpayments.PaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.testutil.TestWebSecurityConfig;
import com.fincity.security.service.plansnbilling.PaymentGatewayService;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { PaymentGatewayController.class, TestWebSecurityConfig.class })
class PaymentGatewayControllerTest {

    private static final String BASE_PATH = "/api/security/payment-gateways";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PaymentGatewayService paymentGatewayService;

    private PaymentGateway sampleGateway;

    @BeforeEach
    void setUp() {
        sampleGateway = new PaymentGateway();
        sampleGateway.setId(ULong.valueOf(1));
        sampleGateway.setClientId(ULong.valueOf(10));
        sampleGateway.setPaymentGateway(SecurityPaymentGatewayPaymentGateway.RAZORPAY);
        sampleGateway.setPaymentGatewayDetails(Map.of(
                "keyId", "rzp_test_abc123",
                "keySecret", "secret_xyz789"));
    }

    // ==================== GET /api/security/payment-gateways/client/{clientId}/gateway/{gateway} ====================

    @Nested
    @DisplayName("GET /api/security/payment-gateways/client/{clientId}/gateway/{gateway}")
    class GetByClientAndGatewayTests {

        @Test
        @DisplayName("Should return 200 with PaymentGateway when found")
        void getByClientAndGateway_found_returns200() {

            when(paymentGatewayService.findByClientIdAndGateway(
                    eq(ULong.valueOf(10)),
                    eq(SecurityPaymentGatewayPaymentGateway.RAZORPAY)))
                    .thenReturn(Mono.just(sampleGateway));

            webTestClient.get()
                    .uri(BASE_PATH + "/client/10/gateway/RAZORPAY")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(1)
                    .jsonPath("$.clientId").isEqualTo(10)
                    .jsonPath("$.paymentGateway").isEqualTo("RAZORPAY")
                    .jsonPath("$.paymentGatewayDetails.keyId").isEqualTo("rzp_test_abc123");

            verify(paymentGatewayService).findByClientIdAndGateway(
                    eq(ULong.valueOf(10)),
                    eq(SecurityPaymentGatewayPaymentGateway.RAZORPAY));
        }

        @Test
        @DisplayName("Should return 404 when no gateway configuration exists for client and gateway type")
        void getByClientAndGateway_notFound_returns404() {

            when(paymentGatewayService.findByClientIdAndGateway(
                    eq(ULong.valueOf(99)),
                    eq(SecurityPaymentGatewayPaymentGateway.STRIPE)))
                    .thenReturn(Mono.empty());

            webTestClient.get()
                    .uri(BASE_PATH + "/client/99/gateway/STRIPE")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody().isEmpty();

            verify(paymentGatewayService).findByClientIdAndGateway(
                    eq(ULong.valueOf(99)),
                    eq(SecurityPaymentGatewayPaymentGateway.STRIPE));
        }
    }

    // ==================== POST /api/security/payment-gateways (inherited CRUD - create) ====================

    @Nested
    @DisplayName("POST /api/security/payment-gateways (inherited create)")
    class CreateTests {

        @Test
        @DisplayName("Should return 200 with created PaymentGateway")
        void create_validEntity_returns200() {

            PaymentGateway newGateway = new PaymentGateway();
            newGateway.setClientId(ULong.valueOf(10));
            newGateway.setPaymentGateway(SecurityPaymentGatewayPaymentGateway.CASHFREE);
            newGateway.setPaymentGatewayDetails(Map.of(
                    "apiKey", "cf_test_key",
                    "apiSecret", "cf_test_secret"));

            PaymentGateway createdGateway = new PaymentGateway();
            createdGateway.setId(ULong.valueOf(2));
            createdGateway.setClientId(ULong.valueOf(10));
            createdGateway.setPaymentGateway(SecurityPaymentGatewayPaymentGateway.CASHFREE);
            createdGateway.setPaymentGatewayDetails(Map.of(
                    "apiKey", "cf_test_key",
                    "apiSecret", "cf_test_secret"));

            when(paymentGatewayService.create(any(PaymentGateway.class)))
                    .thenReturn(Mono.just(createdGateway));

            webTestClient.post()
                    .uri(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(newGateway)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(2)
                    .jsonPath("$.clientId").isEqualTo(10)
                    .jsonPath("$.paymentGateway").isEqualTo("CASHFREE")
                    .jsonPath("$.paymentGatewayDetails.apiKey").isEqualTo("cf_test_key");

            verify(paymentGatewayService).create(any(PaymentGateway.class));
        }

        @Test
        @DisplayName("Should return 500 when service throws RuntimeException during create")
        void create_serviceError_returns500() {

            when(paymentGatewayService.create(any(PaymentGateway.class)))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            PaymentGateway gateway = new PaymentGateway();
            gateway.setClientId(ULong.valueOf(10));
            gateway.setPaymentGateway(SecurityPaymentGatewayPaymentGateway.STRIPE);
            gateway.setPaymentGatewayDetails(Map.of("apiKey", "sk_test_key"));

            webTestClient.post()
                    .uri(BASE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(gateway)
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }
}
