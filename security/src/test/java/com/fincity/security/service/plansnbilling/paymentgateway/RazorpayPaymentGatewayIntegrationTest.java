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

class RazorpayPaymentGatewayIntegrationTest {

	private RazorpayPaymentGatewayIntegration integration;

	@BeforeEach
	void setUp() {
		integration = new RazorpayPaymentGatewayIntegration();
	}

	private PaymentGateway createGateway(Map<String, Object> details) {
		PaymentGateway pg = new PaymentGateway();
		pg.setId(ULong.valueOf(1));
		pg.setClientId(ULong.valueOf(1));
		pg.setPaymentGateway(SecurityPaymentGatewayPaymentGateway.RAZORPAY);
		pg.setPaymentGatewayDetails(details);
		return pg;
	}

	private Invoice createInvoice() {
		Invoice invoice = new Invoice();
		invoice.setId(ULong.valueOf(100));
		invoice.setClientId(ULong.valueOf(1));
		invoice.setInvoiceAmount(BigDecimal.valueOf(1000));
		invoice.setInvoiceNumber("INV-20251001-M00001");
		return invoice;
	}

	// =========================================================================
	// getSupportedGateway() tests
	// =========================================================================

	@Test
	void getSupportedGateway_ReturnsRazorpay() {
		assertEquals(SecurityPaymentGatewayPaymentGateway.RAZORPAY, integration.getSupportedGateway());
	}

	// =========================================================================
	// verifyWebhookSignature() tests
	// =========================================================================

	@Nested
	@DisplayName("verifyWebhookSignature()")
	class VerifyWebhookSignatureTests {

		@Test
		void validSignature_ReturnsTrue() {
			String payload = "{\"event\":\"payment.captured\",\"payload\":{}}";
			String secret = "test_webhook_secret";

			// Generate the expected signature
			try {
				javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
				javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
						secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
				mac.init(keySpec);
				byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				StringBuilder sb = new StringBuilder();
				for (byte b : hash) {
					sb.append(String.format("%02x", b));
				}
				String validSignature = sb.toString();

				assertTrue(integration.verifyWebhookSignature(payload, validSignature, secret));
			} catch (Exception e) {
				fail("Should not throw exception: " + e.getMessage());
			}
		}

		@Test
		void invalidSignature_ReturnsFalse() {
			String payload = "{\"event\":\"payment.captured\"}";
			String secret = "test_webhook_secret";
			String invalidSignature = "invalid_hex_signature_that_does_not_match";

			assertFalse(integration.verifyWebhookSignature(payload, invalidSignature, secret));
		}

		@Test
		void emptyPayload_StillComputes() {
			String payload = "";
			String secret = "secret";

			// Should not throw, just compute HMAC of empty string
			// The result depends on whether there's a matching signature
			assertFalse(integration.verifyWebhookSignature(payload, "wrong", secret));
		}

		@Test
		void differentPayloads_DifferentSignatures() {
			String secret = "my_secret";
			String payload1 = "payload1";
			String payload2 = "payload2";

			// Both should return false with the same wrong signature
			assertFalse(integration.verifyWebhookSignature(payload1, "same_wrong_sig", secret));
			assertFalse(integration.verifyWebhookSignature(payload2, "same_wrong_sig", secret));
		}
	}

	// =========================================================================
	// processCallback() - webhook event mapping tests
	// =========================================================================

	@Nested
	@DisplayName("processCallback() - event mapping")
	class ProcessCallbackEventMappingTests {

		private PaymentGateway gateway;

		@BeforeEach
		void setUp() {
			gateway = createGateway(Map.of("keyId", "rzp_test", "keySecret", "secret"));
		}

		@Test
		void paymentCaptured_MapsToPaid() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("event", "payment.captured");
			Map<String, Object> payload = new HashMap<>();
			Map<String, Object> paymentEntity = new HashMap<>();
			paymentEntity.put("id", "pay_12345");
			paymentEntity.put("status", "captured");
			payload.put("payment", paymentEntity);
			callbackData.put("payload", payload);

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment -> {
						assertEquals("pay_12345", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PAID, payment.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void paymentLinkPaid_MapsToPaid() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("event", "payment_link.paid");
			Map<String, Object> payload = new HashMap<>();
			Map<String, Object> paymentLink = new HashMap<>();
			paymentLink.put("id", "plink_12345");
			payload.put("payment_link", paymentLink);
			callbackData.put("payload", payload);

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment -> {
						assertEquals("plink_12345", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PAID, payment.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void paymentFailed_MapsToFailed() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("event", "payment.failed");
			Map<String, Object> payload = new HashMap<>();
			Map<String, Object> paymentEntity = new HashMap<>();
			paymentEntity.put("id", "pay_failed_123");
			payload.put("payment", paymentEntity);
			callbackData.put("payload", payload);

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment -> {
						assertEquals("pay_failed_123", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.FAILED, payment.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void paymentLinkExpired_MapsToFailed() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("event", "payment_link.expired");
			Map<String, Object> payload = new HashMap<>();
			Map<String, Object> paymentLink = new HashMap<>();
			paymentLink.put("id", "plink_expired");
			payload.put("payment_link", paymentLink);
			callbackData.put("payload", payload);

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.FAILED, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void paymentAuthorized_MapsToPaid() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("event", "payment.authorized");
			Map<String, Object> payload = new HashMap<>();
			Map<String, Object> paymentEntity = new HashMap<>();
			paymentEntity.put("id", "pay_auth_123");
			payload.put("payment", paymentEntity);
			callbackData.put("payload", payload);

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.PAID, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void unknownEvent_MapsToPending() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("event", "payment.some_unknown_event");
			Map<String, Object> payload = new HashMap<>();
			Map<String, Object> paymentEntity = new HashMap<>();
			paymentEntity.put("id", "pay_unknown");
			payload.put("payment", paymentEntity);
			callbackData.put("payload", payload);

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.PENDING, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void noPayloadField_FallsBackToDirectCallback() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("razorpay_payment_id", "pay_direct_123");
			callbackData.put("status", "captured");

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment -> {
						assertEquals("pay_direct_123", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PAID, payment.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void directCallback_PaymentLinkId_UsedAsReference() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("razorpay_payment_link_id", "plink_direct_123");
			callbackData.put("status", "paid");

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment -> {
						assertEquals("plink_direct_123", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PAID, payment.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void directCallback_FailedStatus_MapsToFailed() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("razorpay_payment_id", "pay_fail");
			callbackData.put("status", "failed");

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.FAILED, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void noPaymentEntityInPayload_ThrowsError() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("event", "payment.captured");
			callbackData.put("payload", new HashMap<>()); // Empty payload

			StepVerifier.create(integration.processCallback(gateway, callbackData))
					.expectError(IllegalArgumentException.class)
					.verify();
		}
	}

	// =========================================================================
	// initializePayment() - credential validation tests
	// =========================================================================

	@Nested
	@DisplayName("initializePayment() - credential validation")
	class InitializePaymentCredentialTests {

		@Test
		void missingKeyId_ThrowsError() {
			PaymentGateway gateway = createGateway(Map.of("keySecret", "secret"));
			Invoice invoice = createInvoice();

			StepVerifier.create(integration.initializePayment(invoice, gateway,
					BigDecimal.valueOf(1000), Map.of()))
					.expectError(IllegalArgumentException.class)
					.verify();
		}

		@Test
		void missingKeySecret_ThrowsError() {
			PaymentGateway gateway = createGateway(Map.of("keyId", "rzp_test"));
			Invoice invoice = createInvoice();

			StepVerifier.create(integration.initializePayment(invoice, gateway,
					BigDecimal.valueOf(1000), Map.of()))
					.expectError(IllegalArgumentException.class)
					.verify();
		}
	}
}
