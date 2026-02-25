package com.fincity.security.service.plansnbilling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.plansnbilling.InvoiceDAO;
import com.fincity.security.dao.plansnbilling.PaymentDAO;
import com.fincity.security.dao.plansnbilling.PaymentGatewayDAO;
import com.fincity.security.dto.invoicesnpayments.Invoice;
import com.fincity.security.dto.invoicesnpayments.Payment;
import com.fincity.security.dto.invoicesnpayments.PaymentGateway;
import com.fincity.security.jooq.enums.SecurityInvoiceInvoiceStatus;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentPaymentStatus;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.service.plansnbilling.paymentgateway.IPaymentGatewayIntegration;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest extends AbstractServiceUnitTest {

	@Mock
	private PaymentDAO dao;

	@Mock
	private PaymentGatewayDAO paymentGatewayDAO;

	@Mock
	private InvoiceDAO invoiceDAO;

	@Mock
	private ClientService clientService;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private IPaymentGatewayIntegration paymentGatewayIntegration;

	private PaymentService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong INVOICE_ID = ULong.valueOf(100);
	private static final ULong PAYMENT_ID = ULong.valueOf(200);
	private static final ULong GATEWAY_ID = ULong.valueOf(300);

	@BeforeEach
	void setUp() {
		service = new PaymentService(dao, paymentGatewayDAO, invoiceDAO, clientService,
				messageResourceService, List.of(paymentGatewayIntegration));

		// PaymentService constructor already sets this.dao = dao, so no reflection
		// needed for the DAO. But the superclass field is set directly, verify:
		// PaymentService -> AbstractJOOQUpdatableDataService -> AbstractJOOQDataService
		// (has dao field)
		var daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
		daoField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(daoField, service, dao);

		setupMessageResourceService(messageResourceService);
	}

	private Invoice createInvoice(ULong id, ULong clientId, BigDecimal amount) {
		Invoice invoice = new Invoice();
		invoice.setId(id);
		invoice.setClientId(clientId);
		invoice.setInvoiceAmount(amount);
		invoice.setInvoiceStatus(SecurityInvoiceInvoiceStatus.PENDING);
		invoice.setInvoiceNumber("INV-20251001-M00001");
		return invoice;
	}

	private Payment createPayment(ULong id, ULong invoiceId, SecurityPaymentPaymentStatus status) {
		Payment payment = new Payment();
		payment.setId(id);
		payment.setInvoiceId(invoiceId);
		payment.setPaymentStatus(status);
		payment.setPaymentAmount(BigDecimal.valueOf(1178.82));
		payment.setPaymentReference("pay_ref_001");
		return payment;
	}

	private PaymentGateway createPaymentGateway(ULong id, ULong clientId,
			SecurityPaymentGatewayPaymentGateway gateway) {
		PaymentGateway pg = new PaymentGateway();
		pg.setId(id);
		pg.setClientId(clientId);
		pg.setPaymentGateway(gateway);
		pg.setPaymentGatewayDetails(Map.of("apiKey", "test_key", "apiSecret", "test_secret"));
		return pg;
	}

	// =========================================================================
	// initializePayment() tests
	// =========================================================================

	@Nested
	@DisplayName("initializePayment()")
	class InitializePaymentTests {

		@Test
		void happyPath_CreatesPayment() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Invoice invoice = createInvoice(INVOICE_ID, SYSTEM_CLIENT_ID, BigDecimal.valueOf(1178.82));
			PaymentGateway gateway = createPaymentGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE);
			Payment initializedPayment = createPayment(null, null, SecurityPaymentPaymentStatus.PENDING);
			Payment createdPayment = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.PENDING);

			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.just(invoice));
			when(paymentGatewayDAO.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE)).thenReturn(Mono.just(gateway));
			when(paymentGatewayIntegration.getSupportedGateway())
					.thenReturn(SecurityPaymentGatewayPaymentGateway.CASHFREE);
			when(paymentGatewayIntegration.initializePayment(eq(invoice), eq(gateway), any(), any()))
					.thenReturn(Mono.just(initializedPayment));
			when(dao.create(any(Payment.class))).thenReturn(Mono.just(createdPayment));

			StepVerifier.create(service.initializePayment(INVOICE_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE, Map.of()))
					.assertNext(result -> {
						assertEquals(PAYMENT_ID, result.getId());
						assertEquals(INVOICE_ID, result.getInvoiceId());
					})
					.verifyComplete();
		}

		@Test
		void invoiceNotFound_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.initializePayment(INVOICE_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE, Map.of()))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void gatewayConfigNotFound_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Invoice invoice = createInvoice(INVOICE_ID, SYSTEM_CLIENT_ID, BigDecimal.valueOf(1178.82));

			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.just(invoice));
			when(paymentGatewayDAO.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY)).thenReturn(Mono.empty());

			StepVerifier.create(service.initializePayment(INVOICE_ID,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY, Map.of()))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	// =========================================================================
	// processCallback() tests
	// =========================================================================

	@Nested
	@DisplayName("processCallback()")
	class ProcessCallbackTests {

		@Test
		void happyPath_UpdatesPayment() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway gateway = createPaymentGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE);
			Payment callbackPayment = createPayment(null, null, SecurityPaymentPaymentStatus.PAID);
			callbackPayment.setPaymentReference("pay_ref_001");
			callbackPayment.setPaymentResponse(Map.of("status", "SUCCESS"));
			Payment existingPayment = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.PENDING);
			Payment updatedPayment = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.PAID);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(paymentGatewayDAO.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE)).thenReturn(Mono.just(gateway));
			when(paymentGatewayIntegration.getSupportedGateway())
					.thenReturn(SecurityPaymentGatewayPaymentGateway.CASHFREE);
			when(paymentGatewayIntegration.processCallback(eq(gateway), any()))
					.thenReturn(Mono.just(callbackPayment));
			when(dao.findByPaymentReference("pay_ref_001")).thenReturn(Mono.just(existingPayment));
			when(dao.update(any(Payment.class))).thenReturn(Mono.just(updatedPayment));

			StepVerifier.create(service.processCallback(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE, Map.of("status", "SUCCESS")))
					.assertNext(result -> {
						assertEquals(PAYMENT_ID, result.getId());
						assertEquals(SecurityPaymentPaymentStatus.PAID, result.getPaymentStatus());
					})
					.verifyComplete();
		}

		@Test
		void notManagedClient_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Payment_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.processCallback(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE, Map.of()))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// getPaymentsByInvoiceId() tests
	// =========================================================================

	@Nested
	@DisplayName("getPaymentsByInvoiceId()")
	class GetPaymentsByInvoiceIdTests {

		@Test
		void invoiceFound_ReturnsPayments() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Invoice invoice = createInvoice(INVOICE_ID, SYSTEM_CLIENT_ID, BigDecimal.valueOf(1178.82));
			Payment payment = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.PAID);

			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.just(invoice));
			when(dao.findByInvoiceId(INVOICE_ID)).thenReturn(Mono.just(List.of(payment)));

			StepVerifier.create(service.getPaymentsByInvoiceId(INVOICE_ID))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(1, result.size());
						assertEquals(PAYMENT_ID, result.get(0).getId());
					})
					.verifyComplete();
		}

		@Test
		void invoiceNotFound_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.getPaymentsByInvoiceId(INVOICE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void multiplePayments_ReturnsAll() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Invoice invoice = createInvoice(INVOICE_ID, SYSTEM_CLIENT_ID, BigDecimal.valueOf(1178.82));
			Payment p1 = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.FAILED);
			Payment p2 = createPayment(ULong.valueOf(201), INVOICE_ID, SecurityPaymentPaymentStatus.PAID);

			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.just(invoice));
			when(dao.findByInvoiceId(INVOICE_ID)).thenReturn(Mono.just(List.of(p1, p2)));

			StepVerifier.create(service.getPaymentsByInvoiceId(INVOICE_ID))
					.assertNext(result -> assertEquals(2, result.size()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// updatePaymentStatus() tests
	// =========================================================================

	@Nested
	@DisplayName("updatePaymentStatus()")
	class UpdatePaymentStatusTests {

		@Test
		void pendingToPaid_UpdatesPaymentAndInvoice() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Payment existing = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.PENDING);
			Invoice invoice = createInvoice(INVOICE_ID, SYSTEM_CLIENT_ID, BigDecimal.valueOf(1178.82));
			Payment updated = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.PAID);

			when(dao.readById(PAYMENT_ID)).thenReturn(Mono.just(existing));
			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.just(invoice));
			when(dao.update(any(Payment.class))).thenReturn(Mono.just(updated));
			when(invoiceDAO.update(any(Invoice.class))).thenReturn(Mono.just(invoice));

			StepVerifier.create(service.updatePaymentStatus(PAYMENT_ID, SecurityPaymentPaymentStatus.PAID))
					.assertNext(result -> assertEquals(SecurityPaymentPaymentStatus.PAID, result.getPaymentStatus()))
					.verifyComplete();

			// Verify invoice status was also updated
			verify(invoiceDAO, times(2)).readById(INVOICE_ID);
		}

		@Test
		void pendingToFailed_UpdatesPaymentOnly() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Payment existing = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.PENDING);
			Invoice invoice = createInvoice(INVOICE_ID, SYSTEM_CLIENT_ID, BigDecimal.valueOf(1178.82));
			Payment updated = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.FAILED);

			when(dao.readById(PAYMENT_ID)).thenReturn(Mono.just(existing));
			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.just(invoice));
			when(dao.update(any(Payment.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.updatePaymentStatus(PAYMENT_ID, SecurityPaymentPaymentStatus.FAILED))
					.assertNext(result -> assertEquals(SecurityPaymentPaymentStatus.FAILED, result.getPaymentStatus()))
					.verifyComplete();

			// Invoice update should NOT be called for non-PAID status
			verify(invoiceDAO, times(1)).readById(any());
		}

		@Test
		void paymentNotFound_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.readById(PAYMENT_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.updatePaymentStatus(PAYMENT_ID, SecurityPaymentPaymentStatus.PAID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void invoiceNotFound_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Payment existing = createPayment(PAYMENT_ID, INVOICE_ID, SecurityPaymentPaymentStatus.PENDING);

			when(dao.readById(PAYMENT_ID)).thenReturn(Mono.just(existing));
			when(invoiceDAO.readById(INVOICE_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.updatePaymentStatus(PAYMENT_ID, SecurityPaymentPaymentStatus.PAID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}

	// =========================================================================
	// processCallback() additional tests
	// =========================================================================

	@Nested
	@DisplayName("processCallback() - additional scenarios")
	class ProcessCallbackAdditionalTests {

		@Test
		void nullPaymentReference_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway gateway = createPaymentGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE);
			Payment callbackPayment = createPayment(null, null, SecurityPaymentPaymentStatus.PAID);
			callbackPayment.setPaymentReference(null); // No reference

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(paymentGatewayDAO.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE)).thenReturn(Mono.just(gateway));
			when(paymentGatewayIntegration.getSupportedGateway())
					.thenReturn(SecurityPaymentGatewayPaymentGateway.CASHFREE);
			when(paymentGatewayIntegration.processCallback(eq(gateway), any()))
					.thenReturn(Mono.just(callbackPayment));

			StepVerifier.create(service.processCallback(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE, Map.of()))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void paymentReferenceNotFound_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway gateway = createPaymentGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE);
			Payment callbackPayment = createPayment(null, null, SecurityPaymentPaymentStatus.PAID);
			callbackPayment.setPaymentReference("nonexistent_ref");

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(paymentGatewayDAO.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE)).thenReturn(Mono.just(gateway));
			when(paymentGatewayIntegration.getSupportedGateway())
					.thenReturn(SecurityPaymentGatewayPaymentGateway.CASHFREE);
			when(paymentGatewayIntegration.processCallback(eq(gateway), any()))
					.thenReturn(Mono.just(callbackPayment));
			when(dao.findByPaymentReference("nonexistent_ref")).thenReturn(Mono.empty());

			StepVerifier.create(service.processCallback(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE, Map.of()))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		void gatewayConfigNotFound_ThrowsNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(paymentGatewayDAO.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY)).thenReturn(Mono.empty());

			StepVerifier.create(service.processCallback(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY, Map.of()))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.NOT_FOUND)
					.verify();
		}
	}
}
