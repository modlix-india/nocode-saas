package com.fincity.security.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.testutil.TestWebSecurityConfig;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;
import com.fincity.security.service.plansnbilling.PaymentService;

import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = { PaymentController.class, TestWebSecurityConfig.class })
class PaymentControllerTest {

    private static final String BASE_PATH = "/api/security/payments";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PaymentService paymentService;

    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        samplePayment = new Payment();
        samplePayment.setId(ULong.valueOf(1));
        samplePayment.setInvoiceId(ULong.valueOf(100));
        samplePayment.setPaymentAmount(new BigDecimal("499.99"));
        samplePayment.setPaymentStatus(SecurityPaymentPaymentStatus.PENDING);
        samplePayment.setPaymentReference("PAY-REF-001");
        samplePayment.setPaymentDate(LocalDateTime.of(2026, 2, 18, 10, 30));
    }

    // ==================== POST /api/security/payments/initialize ====================

    @Nested
    @DisplayName("POST /api/security/payments/initialize")
    class InitializePaymentTests {

        @Test
        @DisplayName("Should return 200 with Payment when initialization succeeds")
        void initializePayment_validRequest_returns200() {

            when(paymentService.initializePayment(
                    eq(ULong.valueOf(100)),
                    eq(SecurityPaymentGatewayPaymentGateway.RAZORPAY),
                    any()))
                    .thenReturn(Mono.just(samplePayment));

            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/initialize")
                            .queryParam("invoiceId", "100")
                            .queryParam("gateway", "RAZORPAY")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("returnUrl", "https://example.com/return"))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(1)
                    .jsonPath("$.invoiceId").isEqualTo(100)
                    .jsonPath("$.paymentAmount").isEqualTo(499.99)
                    .jsonPath("$.paymentStatus").isEqualTo("PENDING")
                    .jsonPath("$.paymentReference").isEqualTo("PAY-REF-001");

            verify(paymentService).initializePayment(
                    eq(ULong.valueOf(100)),
                    eq(SecurityPaymentGatewayPaymentGateway.RAZORPAY),
                    any());
        }

        @Test
        @DisplayName("Should return 200 with Payment when metadata body is null")
        void initializePayment_noMetadata_returns200() {

            when(paymentService.initializePayment(
                    eq(ULong.valueOf(100)),
                    eq(SecurityPaymentGatewayPaymentGateway.STRIPE),
                    any()))
                    .thenReturn(Mono.just(samplePayment));

            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/initialize")
                            .queryParam("invoiceId", "100")
                            .queryParam("gateway", "STRIPE")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(1);

            verify(paymentService).initializePayment(
                    eq(ULong.valueOf(100)),
                    eq(SecurityPaymentGatewayPaymentGateway.STRIPE),
                    any());
        }
    }

    // ==================== POST /api/security/payments/callback/{clientId}/{gateway} ====================

    @Nested
    @DisplayName("POST /api/security/payments/callback/{clientId}/{gateway}")
    class ProcessCallbackTests {

        @Test
        @DisplayName("Should return 200 with updated Payment on valid callback")
        void processCallback_validCallback_returns200() {

            Payment updatedPayment = new Payment();
            updatedPayment.setId(ULong.valueOf(1));
            updatedPayment.setInvoiceId(ULong.valueOf(100));
            updatedPayment.setPaymentStatus(SecurityPaymentPaymentStatus.PAID);
            updatedPayment.setPaymentReference("PAY-REF-001");
            updatedPayment.setPaymentDate(LocalDateTime.of(2026, 2, 18, 11, 0));

            Map<String, Object> callbackData = Map.of(
                    "orderId", "order_123",
                    "paymentId", "pay_456",
                    "status", "SUCCESS");

            when(paymentService.processCallback(
                    eq(ULong.valueOf(5)),
                    eq(SecurityPaymentGatewayPaymentGateway.CASHFREE),
                    any()))
                    .thenReturn(Mono.just(updatedPayment));

            webTestClient.post()
                    .uri(BASE_PATH + "/callback/5/CASHFREE")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(callbackData)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.paymentStatus").isEqualTo("PAID")
                    .jsonPath("$.paymentReference").isEqualTo("PAY-REF-001");

            verify(paymentService).processCallback(
                    eq(ULong.valueOf(5)),
                    eq(SecurityPaymentGatewayPaymentGateway.CASHFREE),
                    any());
        }

        @Test
        @DisplayName("Should return 500 when service throws RuntimeException")
        void processCallback_serviceError_returns500() {

            when(paymentService.processCallback(
                    eq(ULong.valueOf(5)),
                    eq(SecurityPaymentGatewayPaymentGateway.RAZORPAY),
                    any()))
                    .thenReturn(Mono.error(new RuntimeException("Unexpected error")));

            webTestClient.post()
                    .uri(BASE_PATH + "/callback/5/RAZORPAY")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("event", "payment.captured"))
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }

    // ==================== GET /api/security/payments/invoice/{invoiceId} ====================

    @Nested
    @DisplayName("GET /api/security/payments/invoice/{invoiceId}")
    class GetPaymentsByInvoiceIdTests {

        @Test
        @DisplayName("Should return 200 with list of Payments for a given invoice")
        void getPaymentsByInvoiceId_validInvoice_returnsList() {

            Payment payment2 = new Payment();
            payment2.setId(ULong.valueOf(2));
            payment2.setInvoiceId(ULong.valueOf(100));
            payment2.setPaymentAmount(new BigDecimal("100.00"));
            payment2.setPaymentStatus(SecurityPaymentPaymentStatus.FAILED);

            when(paymentService.getPaymentsByInvoiceId(eq(ULong.valueOf(100))))
                    .thenReturn(Mono.just(List.of(samplePayment, payment2)));

            webTestClient.get()
                    .uri(BASE_PATH + "/invoice/100")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(2)
                    .jsonPath("$[0].id").isEqualTo(1)
                    .jsonPath("$[0].paymentAmount").isEqualTo(499.99)
                    .jsonPath("$[1].id").isEqualTo(2)
                    .jsonPath("$[1].paymentStatus").isEqualTo("FAILED");

            verify(paymentService).getPaymentsByInvoiceId(eq(ULong.valueOf(100)));
        }

        @Test
        @DisplayName("Should return 200 with empty list when no payments exist for invoice")
        void getPaymentsByInvoiceId_noPayments_returnsEmptyList() {

            when(paymentService.getPaymentsByInvoiceId(eq(ULong.valueOf(999))))
                    .thenReturn(Mono.just(List.of()));

            webTestClient.get()
                    .uri(BASE_PATH + "/invoice/999")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isArray()
                    .jsonPath("$.length()").isEqualTo(0);

            verify(paymentService).getPaymentsByInvoiceId(eq(ULong.valueOf(999)));
        }
    }

    // ==================== POST /api/security/payments/{paymentId}/status ====================

    @Nested
    @DisplayName("POST /api/security/payments/{paymentId}/status")
    class UpdatePaymentStatusTests {

        @Test
        @DisplayName("Should return 200 with updated Payment when status change succeeds")
        void updatePaymentStatus_validRequest_returns200() {

            Payment paidPayment = new Payment();
            paidPayment.setId(ULong.valueOf(1));
            paidPayment.setInvoiceId(ULong.valueOf(100));
            paidPayment.setPaymentStatus(SecurityPaymentPaymentStatus.PAID);
            paidPayment.setPaymentDate(LocalDateTime.of(2026, 2, 18, 12, 0));

            when(paymentService.updatePaymentStatus(
                    eq(ULong.valueOf(1)),
                    eq(SecurityPaymentPaymentStatus.PAID)))
                    .thenReturn(Mono.just(paidPayment));

            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/1/status")
                            .queryParam("status", "PAID")
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(1)
                    .jsonPath("$.paymentStatus").isEqualTo("PAID");

            verify(paymentService).updatePaymentStatus(
                    eq(ULong.valueOf(1)),
                    eq(SecurityPaymentPaymentStatus.PAID));
        }

        @Test
        @DisplayName("Should return 500 when service throws RuntimeException for nonexistent payment")
        void updatePaymentStatus_paymentNotFound_returns500() {

            when(paymentService.updatePaymentStatus(
                    eq(ULong.valueOf(999)),
                    eq(SecurityPaymentPaymentStatus.CANCELLED)))
                    .thenReturn(Mono.error(new RuntimeException("Payment not found")));

            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(BASE_PATH + "/999/status")
                            .queryParam("status", "CANCELLED")
                            .build())
                    .exchange()
                    .expectStatus().is5xxServerError();
        }
    }
}
