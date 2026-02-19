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

class CashfreePaymentGatewayIntegrationTest {

	private CashfreePaymentGatewayIntegration integration;

	@BeforeEach
	void setUp() {
		integration = new CashfreePaymentGatewayIntegration();
	}

	private PaymentGateway createGateway() {
		PaymentGateway pg = new PaymentGateway();
		pg.setId(ULong.valueOf(1));
		pg.setClientId(ULong.valueOf(1));
		pg.setPaymentGateway(SecurityPaymentGatewayPaymentGateway.CASHFREE);
		pg.setPaymentGatewayDetails(Map.of("apiKey", "cf_key", "apiSecret", "cf_secret"));
		return pg;
	}

	private Invoice createInvoice() {
		Invoice invoice = new Invoice();
		invoice.setId(ULong.valueOf(100));
		invoice.setClientId(ULong.valueOf(1));
		invoice.setInvoiceAmount(BigDecimal.valueOf(500));
		invoice.setInvoiceNumber("INV-20251001-M00001");
		return invoice;
	}

	@Test
	void getSupportedGateway_ReturnsCashfree() {
		assertEquals(SecurityPaymentGatewayPaymentGateway.CASHFREE, integration.getSupportedGateway());
	}

	@Nested
	@DisplayName("initializePayment()")
	class InitializePaymentTests {

		@Test
		void createsPaymentWithReference() {
			Invoice invoice = createInvoice();
			PaymentGateway gateway = createGateway();

			StepVerifier.create(integration.initializePayment(invoice, gateway,
					BigDecimal.valueOf(500), Map.of()))
					.assertNext(payment -> {
						assertNotNull(payment.getPaymentReference());
						assertTrue(payment.getPaymentReference().startsWith("CF_"));
						assertNotNull(payment.getPaymentResponse());
						assertEquals("CASHFREE", payment.getPaymentResponse().get("gateway"));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("processCallback()")
	class ProcessCallbackTests {

		@Test
		void paidStatus_MapsToPaid() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("orderId", "order_123");
			callbackData.put("orderStatus", "PAID");

			StepVerifier.create(integration.processCallback(createGateway(), callbackData))
					.assertNext(payment -> {
						assertEquals("order_123", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PAID, payment.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void successStatus_MapsToPaid() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("orderId", "order_456");
			callbackData.put("orderStatus", "SUCCESS");

			StepVerifier.create(integration.processCallback(createGateway(), callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.PAID, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void failedStatus_MapsToFailed() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("orderId", "order_789");
			callbackData.put("orderStatus", "FAILED");

			StepVerifier.create(integration.processCallback(createGateway(), callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.FAILED, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void cancelledStatus_MapsToFailed() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("orderId", "order_cancel");
			callbackData.put("orderStatus", "CANCELLED");

			StepVerifier.create(integration.processCallback(createGateway(), callbackData))
					.assertNext(payment ->
						assertEquals(SecurityPaymentPaymentStatus.FAILED, payment.getPaymentStatus()))
					.verifyComplete();
		}

		@Test
		void pendingStatus_MapsToPending() {
			Map<String, Object> callbackData = new HashMap<>();
			callbackData.put("orderId", "order_pending");
			callbackData.put("orderStatus", "ACTIVE");

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
			StepVerifier.create(integration.checkPaymentStatus(createGateway(), "pay_ref_123"))
					.assertNext(payment -> {
						assertEquals("pay_ref_123", payment.getPaymentReference());
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
			StepVerifier.create(integration.refundPayment(createGateway(), "pay_ref_123",
					BigDecimal.valueOf(100)))
					.assertNext(payment -> {
						assertEquals("pay_ref_123", payment.getPaymentReference());
						assertEquals(SecurityPaymentPaymentStatus.PENDING, payment.getPaymentStatus());
					})
					.verifyComplete();
		}
	}
}
