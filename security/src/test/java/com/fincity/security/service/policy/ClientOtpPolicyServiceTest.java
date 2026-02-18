package com.fincity.security.service.policy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.policy.ClientOtpPolicyDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.jooq.enums.SecurityClientOtpPolicyTargetType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.AbstractServiceUnitTest;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ClientOtpPolicyServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ClientOtpPolicyDAO dao;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private CacheService cacheService;

	@Mock
	private ClientService clientService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private AppService appService;

	private ClientOtpPolicyService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong POLICY_ID = ULong.valueOf(50);

	@BeforeEach
	void setUp() {
		service = new ClientOtpPolicyService(messageResourceService, cacheService);

		// Inject the DAO using reflection.
		// ClientOtpPolicyService -> AbstractPolicyService -> AbstractJOOQUpdatableDataService ->
		// AbstractJOOQDataService (has 'dao' field)
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass().getSuperclass().getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		service.setClientService(clientService);
		service.setClientHierarchyService(clientHierarchyService);
		service.setAppService(appService);

		setupMessageResourceService(messageResourceService);
		setupCacheService(cacheService);
		setupCacheEmptyValueOrGet(cacheService);
	}

	private void setupCacheEmptyValueOrGet(CacheService cs) {
		lenient().when(cs.cacheEmptyValueOrGet(anyString(), any(), any()))
				.thenAnswer(invocation -> {
					java.util.function.Supplier<Mono<?>> supplier = invocation.getArgument(1);
					return supplier.get();
				});
	}

	// ===== checkAllConditions tests =====

	@Nested
	class CheckAllConditions {

		@Test
		void alwaysReturnsTrue_WithClientIdAndAppId() {
			StepVerifier.create(service.checkAllConditions(BUS_CLIENT_ID, APP_ID, USER_ID, "1234"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void alwaysReturnsTrue_WithPolicy() {
			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, "1234"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void alwaysReturnsTrue_WithNullUserId() {
			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();

			StepVerifier.create(service.checkAllConditions(policy, null, "1234"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ===== getDefaultPolicy / getClientAppPolicy tests =====

	@Nested
	class GetPolicy {

		@Test
		void getDefaultPolicy_ReturnsCorrectDefaults() {
			when(clientHierarchyService.getClientHierarchyIds(BUS_CLIENT_ID))
					.thenReturn(Flux.just(BUS_CLIENT_ID));
			when(dao.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.assertNext(result -> {
						assertEquals(ULong.MIN, result.getId());
						assertEquals(SecurityClientOtpPolicyTargetType.EMAIL, result.getTargetType());
						assertFalse(result.isConstant());
						assertTrue(result.isNumeric());
						assertFalse(result.isAlphanumeric());
						assertEquals((short) 4, result.getLength());
						assertFalse(result.isResendSameOtp());
						assertEquals((short) 3, result.getNoResendAttempts());
						assertEquals(Long.valueOf(5L), result.getExpireInterval());
						assertEquals((short) 3, result.getNoFailedAttempts());
						assertEquals(Long.valueOf(15L), result.getUserLockTime());
					})
					.verifyComplete();
		}

		@Test
		void getClientAppPolicy_PolicyExists_ReturnsPolicy() {
			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();
			policy.setId(POLICY_ID);
			policy.setClientId(BUS_CLIENT_ID);
			policy.setAppId(APP_ID);

			when(clientHierarchyService.getClientHierarchyIds(BUS_CLIENT_ID))
					.thenReturn(Flux.just(BUS_CLIENT_ID));
			when(dao.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(policy));

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.assertNext(result -> {
						assertEquals(POLICY_ID, result.getId());
						assertEquals((short) 4, result.getLength());
						assertTrue(result.isNumeric());
					})
					.verifyComplete();
		}
	}

	// ===== CRUD tests =====

	@Nested
	class CrudOperations {

		@Test
		void create_SuccessfullyCreatesPolicy() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();
			policy.setClientId(SYSTEM_CLIENT_ID);
			policy.setAppId(APP_ID);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, "testApp");

			when(appService.getAppByCode(any())).thenReturn(Mono.just(app));
			when(clientHierarchyService.getClientHierarchyIds(SYSTEM_CLIENT_ID))
					.thenReturn(Flux.just(SYSTEM_CLIENT_ID));
			when(dao.getClientAppPolicy(SYSTEM_CLIENT_ID, APP_ID)).thenReturn(Mono.empty());
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(appService.hasWriteAccess(eq(APP_ID), any(ULong.class))).thenReturn(Mono.just(true));
			when(dao.create(any(ClientOtpPolicy.class))).thenReturn(Mono.just(policy));

			StepVerifier.create(service.create(policy))
					.assertNext(result -> {
						assertNotNull(result);
						assertTrue(result.isNumeric());
						assertEquals((short) 4, result.getLength());
					})
					.verifyComplete();
		}
	}

	// ===== Metadata tests =====

	@Nested
	class Metadata {

		@Test
		void getAuthenticationPasswordType_ReturnsOtp() {
			assertEquals(AuthenticationPasswordType.OTP, service.getAuthenticationPasswordType());
		}

		@Test
		void getPolicyCacheName_ReturnsCorrectName() {
			assertEquals("clientOtpPolicy", service.getPolicyCacheName());
		}
	}
}
