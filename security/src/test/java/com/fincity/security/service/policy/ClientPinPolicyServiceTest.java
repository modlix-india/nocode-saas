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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.policy.ClientPinPolicyDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.PastPin;
import com.fincity.security.dto.policy.ClientPinPolicy;
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
class ClientPinPolicyServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ClientPinPolicyDAO dao;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private CacheService cacheService;

	@Mock
	private PasswordEncoder encoder;

	@Mock
	private ClientService clientService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private AppService appService;

	private ClientPinPolicyService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong POLICY_ID = ULong.valueOf(50);

	@BeforeEach
	void setUp() {
		service = new ClientPinPolicyService(messageResourceService, cacheService, encoder);

		// Inject the DAO using reflection.
		// ClientPinPolicyService -> AbstractPolicyService -> AbstractJOOQUpdatableDataService ->
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
		void validPin_ReturnsTrue() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			String validPin = "123456";

			when(dao.getPastPinBasedOnPolicy(any(ClientPinPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, validPin))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void wrongLength_ThrowsBadRequest() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			policy.setLength((short) 6);
			String shortPin = "1234";

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, shortPin))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void wrongLength_TooLong_ThrowsBadRequest() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			policy.setLength((short) 4);
			String longPin = "123456";

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, longPin))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void pastPinMatch_Hashed_ThrowsBadRequest() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			String pin = "123456";

			PastPin pastPin = new PastPin();
			pastPin.setUserId(USER_ID);
			pastPin.setPin("hashedPin");
			pastPin.setPinHashed(true);

			when(dao.getPastPinBasedOnPolicy(any(ClientPinPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.just(pastPin));
			when(encoder.matches(eq(USER_ID + pin), eq("hashedPin")))
					.thenReturn(true);

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, pin))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void pastPinMatch_Unhashed_ThrowsBadRequest() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			String pin = "123456";

			PastPin pastPin = new PastPin();
			pastPin.setUserId(USER_ID);
			pastPin.setPin(pin);
			pastPin.setPinHashed(false);

			when(dao.getPastPinBasedOnPolicy(any(ClientPinPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.just(pastPin));

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, pin))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void pastPinNoMatch_ReturnsTrue() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			String pin = "123456";

			PastPin pastPin = new PastPin();
			pastPin.setUserId(USER_ID);
			pastPin.setPin("differentHash");
			pastPin.setPinHashed(true);

			when(dao.getPastPinBasedOnPolicy(any(ClientPinPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.just(pastPin));
			when(encoder.matches(eq(USER_ID + pin), eq("differentHash")))
					.thenReturn(false);

			StepVerifier.create(service.checkAllConditions(policy, USER_ID, pin))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void nullUserId_SkipsPastPinCheck_ReturnsTrue() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			String pin = "123456";

			StepVerifier.create(service.checkAllConditions(policy, null, pin))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(dao, never()).getPastPinBasedOnPolicy(any(), any());
		}

		@Test
		void checkAllConditions_WithClientIdAndAppId_DelegatesToPolicyCheck() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			policy.setId(POLICY_ID);
			policy.setClientId(BUS_CLIENT_ID);
			policy.setAppId(APP_ID);

			when(clientHierarchyService.getClientHierarchyIds(BUS_CLIENT_ID))
					.thenReturn(Flux.just(BUS_CLIENT_ID));
			when(dao.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(policy));
			when(dao.getPastPinBasedOnPolicy(any(ClientPinPolicy.class), eq(USER_ID)))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.checkAllConditions(BUS_CLIENT_ID, APP_ID, USER_ID, "123456"))
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
						assertEquals((short) 4, result.getLength());
						assertEquals(Long.valueOf(120L), result.getReLoginAfterInterval());
						assertEquals((short) 30, result.getExpiryInDays());
						assertEquals((short) 25, result.getExpiryWarnInDays());
						assertEquals((short) 3, result.getPinHistoryCount());
						assertEquals((short) 3, result.getNoFailedAttempts());
						assertEquals(Long.valueOf(15L), result.getUserLockTime());
					})
					.verifyComplete();
		}

		@Test
		void getClientAppPolicy_PolicyExists_ReturnsPolicy() {
			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
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
						assertEquals((short) 6, result.getLength());
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

			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
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
			when(dao.create(any(ClientPinPolicy.class))).thenReturn(Mono.just(policy));

			StepVerifier.create(service.create(policy))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals((short) 6, result.getLength());
					})
					.verifyComplete();
		}

		@Test
		void update_EvictsCache() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientPinPolicy existing = TestDataFactory.createPinPolicy();
			existing.setId(POLICY_ID);
			existing.setClientId(BUS_CLIENT_ID);
			existing.setAppId(APP_ID);

			ClientPinPolicy updated = TestDataFactory.createPinPolicy();
			updated.setId(POLICY_ID);
			updated.setClientId(BUS_CLIENT_ID);
			updated.setAppId(APP_ID);
			updated.setLength((short) 8);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testApp");

			when(dao.readById(POLICY_ID)).thenReturn(Mono.just(existing));
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(appService.hasWriteAccess(eq(APP_ID), any(ULong.class))).thenReturn(Mono.just(true));
			when(dao.canBeUpdated(POLICY_ID)).thenReturn(Mono.just(true));
			when(dao.update(any(ClientPinPolicy.class))).thenReturn(Mono.just(updated));

			StepVerifier.create(service.update(updated))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();

			verify(cacheService).evict(eq("clientPinPolicy"), eq(BUS_CLIENT_ID + ":" + APP_ID));
		}

		@Test
		void delete_EvictsCache() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientPinPolicy existing = TestDataFactory.createPinPolicy();
			existing.setId(POLICY_ID);
			existing.setClientId(BUS_CLIENT_ID);
			existing.setAppId(APP_ID);

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testApp");

			when(dao.readById(POLICY_ID)).thenReturn(Mono.just(existing));
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));
			when(appService.hasWriteAccess(eq(APP_ID), any(ULong.class))).thenReturn(Mono.just(true));
			when(dao.canBeUpdated(POLICY_ID)).thenReturn(Mono.just(true));
			when(dao.delete(POLICY_ID)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(POLICY_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService).evict(eq("clientPinPolicy"), eq(BUS_CLIENT_ID + ":" + APP_ID));
		}
	}

	// ===== Metadata tests =====

	@Nested
	class Metadata {

		@Test
		void getAuthenticationPasswordType_ReturnsPin() {
			assertEquals(AuthenticationPasswordType.PIN, service.getAuthenticationPasswordType());
		}

		@Test
		void getPolicyCacheName_ReturnsCorrectName() {
			assertEquals("clientPinPolicy", service.getPolicyCacheName());
		}
	}
}
