package com.fincity.security.service.plansnbilling.paymentgateway;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.invoicesnpayments.PaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;

import reactor.test.StepVerifier;

class StripePaymentGatewayIntegrationTest {

	private StripePaymentGatewayIntegration integration;

	@BeforeEach
	void setUp() {
		integration = new StripePaymentGatewayIntegration();
	}

	private PaymentGateway createGateway() {
		PaymentGateway pg = new PaymentGateway();
		pg.setId(ULong.valueOf(1));
		pg.setClientId(ULong.valueOf(1));
		pg.setPaymentGateway(SecurityPaymentGatewayPaymentGateway.STRIPE);
		pg.setPaymentGatewayDetails(Map.of("apiKey", "sk_test_123", "publishableKey", "pk_test_123"));
		return pg;
	}

	private Invoice createInvoice() {
		Invoice invoice = new Invoice();
		invoice.setId(ULong.valueOf(100));
		invoice.setClientId(ULong.valueOf(1));
		invoice.setInvoiceAmount(BigDecimal.valueOf(500));
		invoice.setInvoiceNumber("INV-20251001-S00001");
		return invoice;
	}

	@Test
	void getSupportedGateway_ReturnsStripe() {
		assertEquals(SecurityPaymentGatewayPaymentGateway.STRIPE, integration.getSupportedGateway());
	}

	@Nested
	@DisplayName("initializePayment()")
	class InitializePaymentTests {

		@Test
		void createsPaymentWithStripeReference() {
			Invoice invoice = createInvoice();
			PaymentGateway gateway = createGateway();

			StepVerifier.create(integration.initializePayment(invoice, gateway,
					BigDecimal.valueOf(500), Map.of()))
					.assertNext(payment -> {
						assertNotNull(payment.getPaymentReference());
						assertTrue(payment.getPaymentReference().startsWith("STRIPE_"));
						assertNotNull(payment.getPaymentResponse());
						assertEquals("STRIPE", payment.getPaymentResponse().get("gateway"));
						assertNotNull(payment.getPaymentResponse().get("clientSecret"));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("processCallback()")
	class ProcessCallbackTests {

		@Test
		void succeededStatus_MapsToPaid() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("payment_intent", "pi_12345");
			callbackData.put("status", "succeeded");

			StepVerifier.create(integration.processCallback(createGateway(), callbackData))
					.assertNext(payment -> {
						assertEquals("pi_12345", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PAID, payment.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void failedStatus_MapsToFailed() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("payment_intent", "pi_failed");
			callbackData.put("status", "failed");

			StepVerifier.create(integration.processCallback(createGateway(), callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.FAILED, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void canceledStatus_MapsToFailed() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("payment_intent", "pi_canceled");
			callbackData.put("status", "canceled");

			StepVerifier.create(integration.processCallback(createGateway(), callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.FAILED, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void processingStatus_MapsToPending() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("payment_intent", "pi_processing");
			callbackData.put("status", "processing");

			StepVerifier.create(integration.processCallback(createGateway(), callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.PENDING, payment.getPaymentStatus()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkPaymentStatus()")
	class CheckPaymentStatusTests {

		@Test
		void returnsPaymentWithPendingStatus() {
			StepVerifier.create(integration.checkPaymentStatus(createGateway(), "pi_check"))
					.assertNext(payment -> {
						assertEquals("pi_check", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PENDING, payment.getPaymentStatus());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("refundPayment()")
	class RefundPaymentTests {

		@Test
		void returnsPaymentWithPendingStatus() {
			StepVerifier.create(integration.refundPayment(createGateway(), "pi_refund",
					BigDecimal.valueOf(200)))
					.assertNext(payment -> {
						assertEquals("pi_refund", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PENDING, payment.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void fullRefund_NullAmount() {
			StepVerifier.create(integration.refundPayment(createGateway(), "pi_full_refund", null))
					.assertNext(payment -> {
						assertEquals("pi_full_refund", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PENDING, payment.getPaymentStatus());
					})
					.verifyComplete();
		}
	}
}
