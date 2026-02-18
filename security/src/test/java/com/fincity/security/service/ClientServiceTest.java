package com.fincity.security.service;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.jooq.enums.SecurityClientLevelType;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.policy.ClientOtpPolicyService;
import com.fincity.security.service.policy.ClientPasswordPolicyService;
import com.fincity.security.service.policy.ClientPinPolicyService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ClientDAO dao;

	@Mock
	private CacheService cacheService;

	@Mock
	private AppService appService;

	@Mock
	private UserService userService;

	@Mock
	private SecurityMessageResourceService securityMessageResourceService;

	@Mock
	private AppRegistrationV2DAO appRegistrationDAO;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private ClientPasswordPolicyService clientPasswordPolicyService;

	@Mock
	private ClientPinPolicyService clientPinPolicyService;

	@Mock
	private ClientOtpPolicyService clientOtpPolicyService;

	@Mock
	private ClientManagerService clientManagerService;

	@Mock
	private SoxLogService soxLogService;

	@InjectMocks
	private ClientService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong TARGET_CLIENT_ID = ULong.valueOf(3);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong USER_ID = ULong.valueOf(10);

	@BeforeEach
	void setUp() {
		// Inject the mocked DAO via reflection since AbstractJOOQDataService stores
		// dao in a superclass field
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass()
					.getSuperclass().getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		// Inject soxLogService via reflection since it's @Autowired private in
		// AbstractSecurityUpdatableDataService
		try {
			var soxField = service.getClass().getSuperclass().getDeclaredField("soxLogService");
			soxField.setAccessible(true);
			soxField.set(service, soxLogService);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject SoxLogService", e);
		}

		setupMessageResourceService(securityMessageResourceService);
		setupCacheService(cacheService);
		setupSoxLogService(soxLogService);

		// Mock soxLogService.create() since AbstractSecurityUpdatableDataService uses
		// fire-and-forget sox logging in create/update/delete
		lenient().when(soxLogService.create(any(com.fincity.security.dto.SoxLog.class)))
				.thenReturn(Mono.just(new com.fincity.security.dto.SoxLog()));
	}

	// =========================================================================
	// isUserClientManageClient(ContextAuthentication, ULong)
	// =========================================================================

	@Nested
	@DisplayName("isUserClientManageClient(ContextAuthentication, ULong)")
	class IsUserClientManageClientByIdTests {

		@Test
		void managedAndManager_ReturnsTrue() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.ROLE_Owner", "Authorities.Logged_IN"));

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(clientManagerService.isUserClientManager(ca, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.isUserClientManageClient(ca, TARGET_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void managedButNotManager_ReturnsFalse() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Logged_IN"));

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(clientManagerService.isUserClientManager(ca, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.isUserClientManageClient(ca, TARGET_CLIENT_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void notManaged_ReturnsFalse() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Logged_IN"));

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.isUserClientManageClient(ca, TARGET_CLIENT_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();

			verifyNoInteractions(clientManagerService);
		}

		@Test
		void switchIfEmpty_ReturnsFalse() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Logged_IN"));

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.isUserClientManageClient(ca, TARGET_CLIENT_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// isUserClientManageClient(ContextAuthentication, String)
	// =========================================================================

	@Nested
	@DisplayName("isUserClientManageClient(ContextAuthentication, String)")
	class IsUserClientManageClientByCodeTests {

		@Test
		void delegatesCorrectly_WhenClientFound() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.ROLE_Owner", "Authorities.Logged_IN"));

			Client targetClient = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "TARGETCLIENT");
			when(dao.getClientBy("TARGETCLIENT")).thenReturn(Mono.just(targetClient));

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(clientManagerService.isUserClientManager(ca, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.isUserClientManageClient(ca, "TARGETCLIENT"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void clientNotFound_ReturnsFalse() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Logged_IN"));

			when(dao.getClientBy("UNKNOWN")).thenReturn(Mono.empty());

			StepVerifier.create(service.isUserClientManageClient(ca, "UNKNOWN"))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// doesClientManageClient
	// =========================================================================

	@Nested
	@DisplayName("doesClientManageClient")
	class DoesClientManageClientTests {

		@Test
		void delegatesToHierarchyService() {

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.doesClientManageClient(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(clientHierarchyService).isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID);
		}

		@Test
		void notManaged_ReturnsFalse() {

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.doesClientManageClient(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getClientHierarchy
	// =========================================================================

	@Nested
	@DisplayName("getClientHierarchy")
	class GetClientHierarchyTests {

		@Test
		void delegatesToHierarchyService() {

			List<ULong> expected = List.of(TARGET_CLIENT_ID, BUS_CLIENT_ID, SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchyIdInOrder(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(expected));

			StepVerifier.create(service.getClientHierarchy(TARGET_CLIENT_ID))
					.assertNext(result -> assertEquals(expected, result))
					.verifyComplete();

			verify(clientHierarchyService).getClientHierarchyIdInOrder(TARGET_CLIENT_ID);
		}
	}

	// =========================================================================
	// getManagingClientIds
	// =========================================================================

	@Nested
	@DisplayName("getManagingClientIds")
	class GetManagingClientIdsTests {

		@Test
		void delegatesToHierarchyService() {

			List<ULong> expected = List.of(BUS_CLIENT_ID, SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getManagingClientIds(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(expected));

			StepVerifier.create(service.getManagingClientIds(TARGET_CLIENT_ID))
					.assertNext(result -> assertEquals(expected, result))
					.verifyComplete();

			verify(clientHierarchyService).getManagingClientIds(TARGET_CLIENT_ID);
		}
	}

	// =========================================================================
	// getClientAppPolicy
	// =========================================================================

	@Nested
	@DisplayName("getClientAppPolicy")
	class GetClientAppPolicyTests {

		@BeforeEach
		void initPolicies() {
			lenient().when(clientPasswordPolicyService.getAuthenticationPasswordType())
					.thenReturn(AuthenticationPasswordType.PASSWORD);
			lenient().when(clientPinPolicyService.getAuthenticationPasswordType())
					.thenReturn(AuthenticationPasswordType.PIN);
			lenient().when(clientOtpPolicyService.getAuthenticationPasswordType())
					.thenReturn(AuthenticationPasswordType.OTP);

			service.init();
		}

		@Test
		void passwordType_DelegatesToPasswordPolicyService() {

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			when(clientPasswordPolicyService.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(policy));

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, APP_ID,
					AuthenticationPasswordType.PASSWORD))
					.assertNext(result -> assertEquals(policy, result))
					.verifyComplete();
		}

		@Test
		void pinType_DelegatesToPinPolicyService() {

			ClientPinPolicy policy = TestDataFactory.createPinPolicy();
			when(clientPinPolicyService.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(policy));

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, APP_ID,
					AuthenticationPasswordType.PIN))
					.assertNext(result -> assertEquals(policy, result))
					.verifyComplete();
		}

		@Test
		void otpType_DelegatesToOtpPolicyService() {

			ClientOtpPolicy policy = TestDataFactory.createOtpPolicy();
			when(clientOtpPolicyService.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(policy));

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, APP_ID,
					AuthenticationPasswordType.OTP))
					.assertNext(result -> assertEquals(policy, result))
					.verifyComplete();
		}

		@Test
		void withAppCode_DelegatesToAppServiceThenPolicyService() {

			ClientPasswordPolicy policy = TestDataFactory.createPasswordPolicy();
			when(appService.getAppId("myApp")).thenReturn(Mono.just(APP_ID));
			when(clientPasswordPolicyService.getClientAppPolicy(BUS_CLIENT_ID, APP_ID))
					.thenReturn(Mono.just(policy));

			StepVerifier.create(service.getClientAppPolicy(BUS_CLIENT_ID, "myApp",
					AuthenticationPasswordType.PASSWORD))
					.assertNext(result -> assertEquals(policy, result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// create
	// =========================================================================

	@Nested
	@DisplayName("create")
	class CreateTests {

		@Test
		void systemClient_SkipsHierarchy() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			ca.setClientLevelType("SYSTEM");
			setupSecurityContext(ca);

			Client input = TestDataFactory.createBusinessClient(null, "NEWCLIENT");
			Client created = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "NEWCLIENT");
			created.setLevelType(SecurityClientLevelType.CLIENT);

			when(dao.getPojoClass()).thenReturn(Mono.just(Client.class));
			when(dao.create(any(Client.class))).thenReturn(Mono.just(created));

			securityContextMock.when(com.fincity.saas.commons.security.util.SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.empty());

			StepVerifier.create(service.create(input))
					.assertNext(result -> {
						assertEquals("NEWCLIENT", result.getCode());
						assertEquals(SecurityClientLevelType.CLIENT, result.getLevelType());
					})
					.verifyComplete();

			verifyNoInteractions(clientHierarchyService);
		}

		@Test
		void nonSystemClient_CreatesHierarchy() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_CREATE", "Authorities.Logged_IN"));
			ca.setClientLevelType("CLIENT");
			setupSecurityContext(ca);

			Client input = TestDataFactory.createBusinessClient(null, "NEWCLIENT");
			Client created = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "NEWCLIENT");
			created.setLevelType(SecurityClientLevelType.CUSTOMER);

			when(dao.getPojoClass()).thenReturn(Mono.just(Client.class));
			when(dao.create(any(Client.class))).thenReturn(Mono.just(created));

			securityContextMock.when(com.fincity.saas.commons.security.util.SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.empty());

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(TARGET_CLIENT_ID, BUS_CLIENT_ID);
			when(clientHierarchyService.create(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			StepVerifier.create(service.create(input))
					.assertNext(result -> {
						assertEquals("NEWCLIENT", result.getCode());
						assertEquals(TARGET_CLIENT_ID, result.getId());
					})
					.verifyComplete();

			verify(clientHierarchyService).create(BUS_CLIENT_ID, TARGET_CLIENT_ID);
		}

		@Test
		void setsCorrectLevelType_ForSystemParent() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			ca.setClientLevelType("SYSTEM");
			setupSecurityContext(ca);

			Client input = new Client();
			input.setCode("TESTLEVEL");
			input.setName("Test Level");
			input.setTypeCode("BUS");
			input.setStatusCode(SecurityClientStatusCode.ACTIVE);

			Client created = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "TESTLEVEL");
			created.setLevelType(SecurityClientLevelType.CLIENT);

			when(dao.getPojoClass()).thenReturn(Mono.just(Client.class));
			when(dao.create(any(Client.class))).thenReturn(Mono.just(created));

			securityContextMock.when(com.fincity.saas.commons.security.util.SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.empty());

			StepVerifier.create(service.create(input))
					.assertNext(result -> assertEquals(SecurityClientLevelType.CLIENT, result.getLevelType()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// update(Client)
	// =========================================================================

	@Nested
	@DisplayName("update(Client)")
	class UpdateClientTests {

		@Test
		void evictsBothCaches() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Client entity = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "UPDATED");

			when(dao.getPojoClass()).thenReturn(Mono.just(Client.class));
			when(dao.update(any(Client.class))).thenReturn(Mono.just(entity));

			securityContextMock.when(com.fincity.saas.commons.security.util.SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.empty());

			StepVerifier.create(service.update(entity))
					.assertNext(result -> assertEquals("UPDATED", result.getCode()))
					.verifyComplete();

			verify(cacheService).evict("clientCodeId", BUS_CLIENT_ID);
			verify(cacheService).evict("clientId", BUS_CLIENT_ID);
		}
	}

	// =========================================================================
	// update(ULong, Map)
	// =========================================================================

	@Nested
	@DisplayName("update(ULong, Map)")
	class UpdateFieldsTests {

		@Test
		void evictsBothCaches() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Client existing = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "EXISTING");
			Client updated = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "EXISTING");
			updated.setName("Updated Name");

			when(dao.readById(BUS_CLIENT_ID)).thenReturn(Mono.just(existing));
			when(dao.getPojoClass()).thenReturn(Mono.just(Client.class));
			when(dao.update(any(Client.class))).thenReturn(Mono.just(updated));

			securityContextMock.when(com.fincity.saas.commons.security.util.SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.empty());

			com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			try {
				var omField = service.getClass().getSuperclass().getSuperclass()
						.getDeclaredField("objectMapper");
				omField.setAccessible(true);
				omField.set(service, objectMapper);
			} catch (Exception e) {
				throw new RuntimeException("Failed to inject ObjectMapper", e);
			}

			Map<String, Object> fields = Map.of("name", "Updated Name");

			StepVerifier.create(service.update(BUS_CLIENT_ID, fields))
					.assertNext(result -> assertEquals("Updated Name", result.getName()))
					.verifyComplete();

			verify(cacheService, atLeast(1)).evict("clientCodeId", BUS_CLIENT_ID);
			verify(cacheService, atLeast(1)).evict("clientId", BUS_CLIENT_ID);
		}
	}

	// =========================================================================
	// delete
	// =========================================================================

	@Nested
	@DisplayName("delete")
	class DeleteTests {

		@Test
		void readsSetsDELETEDAndEvictsCaches() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Client existing = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "TOBEDELETED");

			// read calls dao.readById
			when(dao.readById(BUS_CLIENT_ID)).thenReturn(Mono.just(existing));
			when(dao.getPojoClass()).thenReturn(Mono.just(Client.class));
			when(dao.update(any(Client.class))).thenAnswer(invocation -> {
				Client c = invocation.getArgument(0);
				assertEquals(SecurityClientStatusCode.DELETED, c.getStatusCode());
				return Mono.just(c);
			});

			securityContextMock.when(com.fincity.saas.commons.security.util.SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.empty());

			StepVerifier.create(service.delete(BUS_CLIENT_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			verify(cacheService, atLeastOnce()).evict("clientCodeId", BUS_CLIENT_ID);
			verify(cacheService, atLeastOnce()).evict("clientId", BUS_CLIENT_ID);
		}
	}

	// =========================================================================
	// validatePasswordPolicy
	// =========================================================================

	@Nested
	@DisplayName("validatePasswordPolicy")
	class ValidatePasswordPolicyTests {

		@BeforeEach
		void initPolicies() {
			lenient().when(clientPasswordPolicyService.getAuthenticationPasswordType())
					.thenReturn(AuthenticationPasswordType.PASSWORD);
			lenient().when(clientPinPolicyService.getAuthenticationPasswordType())
					.thenReturn(AuthenticationPasswordType.PIN);
			lenient().when(clientOtpPolicyService.getAuthenticationPasswordType())
					.thenReturn(AuthenticationPasswordType.OTP);

			service.init();
		}

		@Test
		void validPassword_ReturnsTrue() {

			when(clientPasswordPolicyService.checkAllConditions(BUS_CLIENT_ID, APP_ID, USER_ID, "StrongP@ss1234"))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.validatePasswordPolicy(BUS_CLIENT_ID, APP_ID, USER_ID,
					AuthenticationPasswordType.PASSWORD, "StrongP@ss1234"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void invalidPassword_ThrowsError() {

			when(clientPasswordPolicyService.checkAllConditions(BUS_CLIENT_ID, APP_ID, USER_ID, "weak"))
					.thenReturn(Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "Password too weak")));

			StepVerifier.create(service.validatePasswordPolicy(BUS_CLIENT_ID, APP_ID, USER_ID,
					AuthenticationPasswordType.PASSWORD, "weak"))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void emptyCheckResult_ReturnsTrue() {

			when(clientPasswordPolicyService.checkAllConditions(BUS_CLIENT_ID, APP_ID, USER_ID, "noPolicy"))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.validatePasswordPolicy(BUS_CLIENT_ID, APP_ID, USER_ID,
					AuthenticationPasswordType.PASSWORD, "noPolicy"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void withAppCode_DelegatesToAppServiceFirst() {

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "testApp");
			when(appService.getAppByCode("testApp")).thenReturn(Mono.just(app));
			when(clientPasswordPolicyService.checkAllConditions(BUS_CLIENT_ID, APP_ID, USER_ID, "Valid@123456"))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.validatePasswordPolicy(BUS_CLIENT_ID, "testApp", USER_ID,
					AuthenticationPasswordType.PASSWORD, "Valid@123456"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// makeClientActiveIfInActive
	// =========================================================================

	@Nested
	@DisplayName("makeClientActiveIfInActive")
	class MakeClientActiveIfInActiveTests {

		@Test
		void systemClient_ActivatesSuccessfully() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.makeClientActiveIfInActive(TARGET_CLIENT_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.makeClientActiveIfInActive(TARGET_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void managedClient_ActivatesSuccessfully() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.ROLE_Owner", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(clientManagerService.isUserClientManager(ca, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(dao.makeClientActiveIfInActive(TARGET_CLIENT_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.makeClientActiveIfInActive(TARGET_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void notManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.makeClientActiveIfInActive(TARGET_CLIENT_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void nullId_UsesLoggedInClientId() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.makeClientActiveIfInActive(isNull())).thenReturn(Mono.just(true));

			StepVerifier.create(service.makeClientActiveIfInActive(null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// makeClientInActive
	// =========================================================================

	@Nested
	@DisplayName("makeClientInActive")
	class MakeClientInActiveTests {

		@Test
		void managedClient_DeactivatesSuccessfully() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.ROLE_Owner", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(clientManagerService.isUserClientManager(ca, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(dao.makeClientInActive(TARGET_CLIENT_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.makeClientInActive(TARGET_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void systemClient_DeactivatesSuccessfully() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.makeClientInActive(TARGET_CLIENT_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.makeClientInActive(TARGET_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void notManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Client_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.makeClientInActive(TARGET_CLIENT_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// =========================================================================
	// getClientLevelType
	// =========================================================================

	@Nested
	@DisplayName("getClientLevelType")
	class GetClientLevelTypeTests {

		@Test
		void ownerCase_WhenAppClientIdMatchesClientId() {

			App app = TestDataFactory.createOwnApp(APP_ID, TARGET_CLIENT_ID, "ownerApp");
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(TARGET_CLIENT_ID, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			StepVerifier.create(service.getClientLevelType(TARGET_CLIENT_ID, APP_ID))
					.assertNext(result -> assertEquals(ClientLevelType.OWNER, result))
					.verifyComplete();
		}

		@Test
		void clientCase_WhenAppClientIdMatchesLevel0() {

			App app = TestDataFactory.createOwnApp(APP_ID, BUS_CLIENT_ID, "clientApp");
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(TARGET_CLIENT_ID, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			StepVerifier.create(service.getClientLevelType(TARGET_CLIENT_ID, APP_ID))
					.assertNext(result -> assertEquals(ClientLevelType.CLIENT, result))
					.verifyComplete();
		}

		@Test
		void customerCase_WhenAppClientIdMatchesLevel1() {

			ULong level1ClientId = ULong.valueOf(50);
			App app = TestDataFactory.createOwnApp(APP_ID, level1ClientId, "customerApp");
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));

			ClientHierarchy hierarchy = TestDataFactory.createClientHierarchy(TARGET_CLIENT_ID,
					BUS_CLIENT_ID, level1ClientId, null, null);
			when(clientHierarchyService.getClientHierarchy(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			StepVerifier.create(service.getClientLevelType(TARGET_CLIENT_ID, APP_ID))
					.assertNext(result -> assertEquals(ClientLevelType.CUSTOMER, result))
					.verifyComplete();
		}

		@Test
		void consumerCase_WhenAppClientIdMatchesNoSpecificLevel() {

			ULong otherClientId = ULong.valueOf(99);
			App app = TestDataFactory.createOwnApp(APP_ID, otherClientId, "consumerApp");
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));

			ClientHierarchy hierarchy = TestDataFactory.createClientHierarchy(TARGET_CLIENT_ID,
					BUS_CLIENT_ID, ULong.valueOf(50), otherClientId, null);
			when(clientHierarchyService.getClientHierarchy(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			StepVerifier.create(service.getClientLevelType(TARGET_CLIENT_ID, APP_ID))
					.assertNext(result -> assertEquals(ClientLevelType.CONSUMER, result))
					.verifyComplete();
		}

		@Test
		void clientNotInHierarchy_ReturnsEmpty() {

			ULong otherClientId = ULong.valueOf(99);
			App app = TestDataFactory.createOwnApp(APP_ID, otherClientId, "noHierarchyApp");
			when(appService.getAppById(APP_ID)).thenReturn(Mono.just(app));

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(ULong.valueOf(77), BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			StepVerifier.create(service.getClientLevelType(TARGET_CLIENT_ID, APP_ID))
					.verifyComplete();
		}
	}

	// =========================================================================
	// isClientActive
	// =========================================================================

	@Nested
	@DisplayName("isClientActive")
	class IsClientActiveTests {

		@Test
		void activeClient_ReturnsTrue() {

			List<ULong> hierarchy = List.of(TARGET_CLIENT_ID, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchyIdInOrder(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));
			when(dao.isClientActive(hierarchy)).thenReturn(Mono.just(true));

			StepVerifier.create(service.isClientActive(TARGET_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void inactiveClient_ReturnsFalse() {

			List<ULong> hierarchy = List.of(TARGET_CLIENT_ID, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchyIdInOrder(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));
			when(dao.isClientActive(hierarchy)).thenReturn(Mono.just(false));

			StepVerifier.create(service.isClientActive(TARGET_CLIENT_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getActiveClient
	// =========================================================================

	@Nested
	@DisplayName("getActiveClient")
	class GetActiveClientTests {

		@Test
		void activeClient_ReturnsClient() {

			List<ULong> hierarchy = List.of(TARGET_CLIENT_ID, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchyIdInOrder(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));
			when(dao.isClientActive(hierarchy)).thenReturn(Mono.just(true));

			Client client = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "ACTIVE");
			when(dao.readInternal(TARGET_CLIENT_ID)).thenReturn(Mono.just(client));

			StepVerifier.create(service.getActiveClient(TARGET_CLIENT_ID))
					.assertNext(result -> assertEquals("ACTIVE", result.getCode()))
					.verifyComplete();
		}

		@Test
		void inactiveClient_ThrowsBadRequest() {

			List<ULong> hierarchy = List.of(TARGET_CLIENT_ID, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchyIdInOrder(TARGET_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));
			when(dao.isClientActive(hierarchy)).thenReturn(Mono.just(false));

			StepVerifier.create(service.getActiveClient(TARGET_CLIENT_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}
	}

	// =========================================================================
	// getSystemClientId
	// =========================================================================

	@Nested
	@DisplayName("getSystemClientId")
	class GetSystemClientIdTests {

		@Test
		void usesCache() {

			when(dao.getSystemClientId()).thenReturn(Mono.just(SYSTEM_CLIENT_ID));

			StepVerifier.create(service.getSystemClientId())
					.assertNext(result -> assertEquals(SYSTEM_CLIENT_ID, result))
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("CACHE_SYSTEM_CLIENT_ID"), any(), eq("SYSTEM"));
		}
	}

	// =========================================================================
	// getManagedClientOfClientById
	// =========================================================================

	@Nested
	@DisplayName("getManagedClientOfClientById")
	class GetManagedClientOfClientByIdTests {

		@Test
		void usesCache_AndDelegatesToHierarchyService() {

			Client managingClient = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "MANAGING");
			when(clientHierarchyService.getManagingClient(TARGET_CLIENT_ID, ClientHierarchy.Level.ZERO))
					.thenReturn(Mono.just(BUS_CLIENT_ID));
			when(dao.readInternal(BUS_CLIENT_ID)).thenReturn(Mono.just(managingClient));

			StepVerifier.create(service.getManagedClientOfClientById(TARGET_CLIENT_ID))
					.assertNext(result -> assertEquals("MANAGING", result.getCode()))
					.verifyComplete();
		}

		@Test
		void noManagingClient_ReturnsDefaultEmptyClient() {

			when(clientHierarchyService.getManagingClient(TARGET_CLIENT_ID, ClientHierarchy.Level.ZERO))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.getManagedClientOfClientById(TARGET_CLIENT_ID))
					.assertNext(result -> assertNull(result.getCode()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// readInternal
	// =========================================================================

	@Nested
	@DisplayName("readInternal")
	class ReadInternalTests {

		@Test
		void usesCache() {

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "CACHED");
			when(dao.readInternal(BUS_CLIENT_ID)).thenReturn(Mono.just(client));

			StepVerifier.create(service.readInternal(BUS_CLIENT_ID))
					.assertNext(result -> assertEquals("CACHED", result.getCode()))
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("clientId"), any(), eq(BUS_CLIENT_ID));
		}

		@Test
		void notFound_ReturnsEmpty() {

			when(dao.readInternal(ULong.valueOf(999))).thenReturn(Mono.empty());

			StepVerifier.create(service.readInternal(ULong.valueOf(999)))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getClientBy(String)
	// =========================================================================

	@Nested
	@DisplayName("getClientBy(String)")
	class GetClientByCodeTests {

		@Test
		void usesCache() {

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "MYCODE");
			when(dao.getClientBy("MYCODE")).thenReturn(Mono.just(client));

			StepVerifier.create(service.getClientBy("MYCODE"))
					.assertNext(result -> {
						assertEquals(BUS_CLIENT_ID, result.getId());
						assertEquals("MYCODE", result.getCode());
					})
					.verifyComplete();

			verify(cacheService).cacheValueOrGet(eq("clientCodeId"), any(), eq("MYCODE"));
		}

		@Test
		void notFound_ReturnsEmpty() {

			when(dao.getClientBy("NONEXISTENT")).thenReturn(Mono.empty());

			StepVerifier.create(service.getClientBy("NONEXISTENT"))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getClientId(String)
	// =========================================================================

	@Nested
	@DisplayName("getClientId")
	class GetClientIdTests {

		@Test
		void returnsId_WhenClientFound() {

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "IDTEST");
			when(dao.getClientBy("IDTEST")).thenReturn(Mono.just(client));

			StepVerifier.create(service.getClientId("IDTEST"))
					.assertNext(result -> assertEquals(BUS_CLIENT_ID, result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getClientInfoById
	// =========================================================================

	@Nested
	@DisplayName("getClientInfoById")
	class GetClientInfoByIdTests {

		@Test
		void byULong_DelegatesToReadInternal() {

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "INFO");
			when(dao.readInternal(BUS_CLIENT_ID)).thenReturn(Mono.just(client));

			StepVerifier.create(service.getClientInfoById(BUS_CLIENT_ID))
					.assertNext(result -> assertEquals("INFO", result.getCode()))
					.verifyComplete();
		}

		@Test
		void byBigInteger_DelegatesToReadInternal() {

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "BIGINTINFO");
			when(dao.readInternal(BUS_CLIENT_ID)).thenReturn(Mono.just(client));

			StepVerifier
					.create(service.getClientInfoById(java.math.BigInteger.valueOf(BUS_CLIENT_ID.longValue())))
					.assertNext(result -> assertEquals("BIGINTINFO", result.getCode()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// isUserClientManageClient(String, ULong, ULong, ULong) - 3-arg appCode
	// variant
	// =========================================================================

	@Nested
	@DisplayName("isUserClientManageClient(String, ULong, ULong, ULong)")
	class IsUserClientManageClientByAppCodeTests {

		@Test
		void managedAndManager_ReturnsTrue() {

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));
			when(clientManagerService.isUserClientManager("appCode", USER_ID, BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(true));

			StepVerifier
					.create(service.isUserClientManageClient("appCode", USER_ID, BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void notManaged_ReturnsFalse() {

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier
					.create(service.isUserClientManageClient("appCode", USER_ID, BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void switchIfEmpty_ReturnsFalse() {

			when(clientHierarchyService.isClientBeingManagedBy(BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.thenReturn(Mono.empty());

			StepVerifier
					.create(service.isUserClientManageClient("appCode", USER_ID, BUS_CLIENT_ID, TARGET_CLIENT_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getSoxObjectName
	// =========================================================================

	@Nested
	@DisplayName("getSoxObjectName")
	class GetSoxObjectNameTests {

		@Test
		void returnsCLIENT() {
			assertEquals(com.fincity.security.jooq.enums.SecuritySoxLogObjectName.CLIENT,
					service.getSoxObjectName());
		}
	}

	// =========================================================================
	// fillManagingClientDetails
	// =========================================================================

	@Nested
	@DisplayName("fillManagingClientDetails")
	class FillManagingClientDetailsTests {

		@Test
		void setsManagingClient() {

			Client client = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "FILLME");
			Client managingClient = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "MANAGING");

			when(clientHierarchyService.getManagingClient(TARGET_CLIENT_ID, ClientHierarchy.Level.ZERO))
					.thenReturn(Mono.just(BUS_CLIENT_ID));
			when(dao.readInternal(BUS_CLIENT_ID)).thenReturn(Mono.just(managingClient));

			StepVerifier.create(service.fillManagingClientDetails(client))
					.assertNext(result -> {
						assertNotNull(result.getManagagingClient());
						assertEquals("MANAGING", result.getManagagingClient().getCode());
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// getClientsBy
	// =========================================================================

	@Nested
	@DisplayName("getClientsBy")
	class GetClientsByTests {

		@Test
		void delegatesToDAO() {

			List<ULong> ids = List.of(BUS_CLIENT_ID, TARGET_CLIENT_ID);
			Client c1 = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "C1");
			Client c2 = TestDataFactory.createBusinessClient(TARGET_CLIENT_ID, "C2");

			when(dao.getClientsBy(ids)).thenReturn(Mono.just(List.of(c1, c2)));

			StepVerifier.create(service.getClientsBy(ids))
					.assertNext(result -> assertEquals(2, result.size()))
					.verifyComplete();
		}
	}
}
