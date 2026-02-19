package com.fincity.security.service.plansnbilling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import com.fincity.security.dao.plansnbilling.PaymentGatewayDAO;
import com.fincity.security.dto.invoicesnpayments.PaymentGateway;
import com.fincity.security.jooq.enums.SecurityPaymentGatewayPaymentGateway;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest extends AbstractServiceUnitTest {

	@Mock
	private PaymentGatewayDAO dao;

	@Mock
	private ClientService clientService;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	private PaymentGatewayService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong GATEWAY_ID = ULong.valueOf(100);

	@BeforeEach
	void setUp() {
		service = new PaymentGatewayService(dao, clientService, messageResourceService);

		// PaymentGatewayService constructor already sets this.dao = dao, but ensure
		// the superclass field is also set via reflection.
		// PaymentGatewayService -> AbstractJOOQUpdatableDataService ->
		// AbstractJOOQDataService (has dao field)
		var daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
		daoField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(daoField, service, dao);

		setupMessageResourceService(messageResourceService);
	}

	private PaymentGateway createGateway(ULong id, ULong clientId,
			SecurityPaymentGatewayPaymentGateway gateway, Map<String, Object> details) {
		PaymentGateway pg = new PaymentGateway();
		pg.setId(id);
		pg.setClientId(clientId);
		pg.setPaymentGateway(gateway);
		pg.setPaymentGatewayDetails(details);
		return pg;
	}

	// =========================================================================
	// create() tests
	// =========================================================================

	@Nested
	@DisplayName("create()")
	class CreateTests {

		@Test
		void systemClient_NullClientId_SetsFromContext() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of("apiKey", "key123", "apiSecret", "secret123"));

			PaymentGateway created = createGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of("apiKey", "key123", "apiSecret", "secret123"));

			when(dao.create(any(PaymentGateway.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> {
						assertEquals(GATEWAY_ID, result.getId());
						assertEquals(SYSTEM_CLIENT_ID, result.getClientId());
					})
					.verifyComplete();
		}

		@Test
		void nonSystemClient_ChecksManagement() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Payment_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, BUS_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY,
					Map.of("keyId", "rzp_key", "keySecret", "rzp_secret"));

			PaymentGateway created = createGateway(GATEWAY_ID, BUS_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY,
					Map.of("keyId", "rzp_key", "keySecret", "rzp_secret"));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.create(any(PaymentGateway.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> {
						assertEquals(GATEWAY_ID, result.getId());
						assertEquals(BUS_CLIENT_ID, result.getClientId());
					})
					.verifyComplete();

			verify(clientService).isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID));
		}

		@Test
		void notManaged_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Payment_CREATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, ULong.valueOf(99),
					SecurityPaymentGatewayPaymentGateway.STRIPE,
					Map.of("apiKey", "sk_test_123"));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(ULong.valueOf(99))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void missingDetails_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.CASHFREE, null);

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	// =========================================================================
	// update() tests
	// =========================================================================

	@Nested
	@DisplayName("update()")
	class UpdateTests {

		@Test
		void managedClient_UpdatesSuccessfully() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of("apiKey", "new_key", "apiSecret", "new_secret"));

			PaymentGateway existing = createGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of("apiKey", "old_key", "apiSecret", "old_secret"));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(dao.readById(GATEWAY_ID)).thenReturn(Mono.just(existing));
			when(dao.update(any(PaymentGateway.class))).thenReturn(Mono.just(entity));

			StepVerifier.create(service.update(entity))
					.assertNext(result -> {
						assertEquals(GATEWAY_ID, result.getId());
						assertEquals("new_key", result.getPaymentGatewayDetails().get("apiKey"));
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// findByClientIdAndGateway() tests
	// =========================================================================

	@Nested
	@DisplayName("findByClientIdAndGateway()")
	class FindByClientIdAndGatewayTests {

		@Test
		void found_ReturnsGateway() {
			PaymentGateway gateway = createGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of("apiKey", "key123", "apiSecret", "secret123"));

			when(dao.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE)).thenReturn(Mono.just(gateway));

			StepVerifier.create(service.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.CASHFREE))
					.assertNext(result -> {
						assertEquals(GATEWAY_ID, result.getId());
						assertEquals(SecurityPaymentGatewayPaymentGateway.CASHFREE, result.getPaymentGateway());
					})
					.verifyComplete();
		}

		@Test
		void notFound_ReturnsEmpty() {
			when(dao.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.STRIPE)).thenReturn(Mono.empty());

			StepVerifier.create(service.findByClientIdAndGateway(SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.STRIPE))
					.verifyComplete();
		}
	}

	// =========================================================================
	// Validation tests - gateway-specific detail validation
	// =========================================================================

	@Nested
	@DisplayName("create() - gateway-specific validation")
	class GatewayValidationTests {

		@Test
		void cashfree_MissingApiKey_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of("apiSecret", "secret123")); // Missing apiKey

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void cashfree_MissingApiSecret_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of("apiKey", "key123")); // Missing apiSecret

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void razorpay_MissingKeyId_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY,
					Map.of("keySecret", "rzp_secret")); // Missing keyId

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void razorpay_MissingKeySecret_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY,
					Map.of("keyId", "rzp_key")); // Missing keySecret

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void razorpay_ValidDetails_Succeeds() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY,
					Map.of("keyId", "rzp_key", "keySecret", "rzp_secret"));

			PaymentGateway created = createGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.RAZORPAY,
					Map.of("keyId", "rzp_key", "keySecret", "rzp_secret"));

			when(dao.create(any(PaymentGateway.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> assertEquals(GATEWAY_ID, result.getId()))
					.verifyComplete();
		}

		@Test
		void stripe_MissingApiKey_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.STRIPE,
					Map.of("publishableKey", "pk_test_123")); // Missing apiKey

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void stripe_ValidDetails_Succeeds() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.STRIPE,
					Map.of("apiKey", "sk_test_123"));

			PaymentGateway created = createGateway(GATEWAY_ID, SYSTEM_CLIENT_ID,
					SecurityPaymentGatewayPaymentGateway.STRIPE,
					Map.of("apiKey", "sk_test_123"));

			when(dao.create(any(PaymentGateway.class))).thenReturn(Mono.just(created));

			StepVerifier.create(service.create(entity))
					.assertNext(result -> assertEquals(GATEWAY_ID, result.getId()))
					.verifyComplete();
		}

		@Test
		void emptyDetails_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(null, null,
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of()); // Empty details

			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	// =========================================================================
	// update() - additional tests
	// =========================================================================

	@Nested
	@DisplayName("update() - access control")
	class UpdateAccessControlTests {

		@Test
		void notManaged_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Payment_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			PaymentGateway entity = createGateway(GATEWAY_ID, ULong.valueOf(99),
					SecurityPaymentGatewayPaymentGateway.CASHFREE,
					Map.of("apiKey", "key", "apiSecret", "secret"));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(ULong.valueOf(99))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.update(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}
}
