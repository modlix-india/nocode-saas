package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentMatchers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.RequestUpdatePassword;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
class UserServiceTest extends AbstractServiceUnitTest {

	@Mock
	private UserDAO dao;

	@Mock
	private ClientService clientService;

	@Mock
	private AppService appService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private SoxLogService soxLogService;

	@Mock
	private OtpService otpService;

	@Mock
	private TokenService tokenService;

	@Mock
	private EventCreationService ecService;

	@Mock
	private AppRegistrationV2DAO appRegistrationDAO;

	@Mock
	private ProfileService profileService;

	@Mock
	private DepartmentService departmentService;

	@Mock
	private DesignationService designationService;

	@Mock
	private CacheService cacheService;

	@Mock
	private RoleV2Service roleService;

	@Mock
	private UserSubOrganizationService userSubOrgService;

	@Mock
	private ClientManagerService clientManagerService;

	private ObjectMapper objectMapper = new ObjectMapper();

	private UserService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong OTHER_USER_ID = ULong.valueOf(20);
	private static final ULong ROLE_ID = ULong.valueOf(100);
	private static final ULong PROFILE_ID = ULong.valueOf(200);
	private static final ULong DESIGNATION_ID = ULong.valueOf(300);
	private static final String APP_CODE = "testApp";
	private static final String CLIENT_CODE = "TESTCLIENT";

	@BeforeEach
	void setUp() {
		service = new UserService(
				clientService, appService, clientHierarchyService, passwordEncoder,
				messageResourceService, soxLogService, otpService, tokenService,
				ecService, appRegistrationDAO, profileService, departmentService,
				designationService, cacheService, roleService);

		injectDao();
		injectLazyDependencies();

		lenient().when(dao.getPojoClass()).thenReturn(Mono.just(User.class));

		var omField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "objectMapper");
		omField.setAccessible(true);
		try {
			omField.set(service, objectMapper);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject objectMapper", e);
		}

		// Inject soxLogService into AbstractSecurityUpdatableDataService parent
		var parentSoxLogField = org.springframework.util.ReflectionUtils.findField(
				com.fincity.security.service.AbstractSecurityUpdatableDataService.class, "soxLogService");
		if (parentSoxLogField != null) {
			parentSoxLogField.setAccessible(true);
			try {
				parentSoxLogField.set(service, soxLogService);
			} catch (Exception e) {
				throw new RuntimeException("Failed to inject soxLogService into parent", e);
			}
		}

		setupMessageResourceService(messageResourceService);
		setupCacheService(cacheService);
		setupSoxLogService(soxLogService);

		lenient().when(soxLogService.create(any())).thenReturn(Mono.just(new com.fincity.security.dto.SoxLog()));

		setField(service, "tokenKey",
				"testSecretKeyForJWTTokenGenerationThatIsLongEnoughForHS256Algorithm1234567890");
	}

	private void injectDao() {
		try {
			var daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}
	}

	private void injectLazyDependencies() {
		setField(service, "userSubOrgService", userSubOrgService);
		setField(service, "clientManagerService", clientManagerService);
	}

	private void setField(Object target, String fieldName, Object value) {
		try {
			var field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set field: " + fieldName, e);
		}
	}

	private void setupEvictCacheMocks(ULong userId, ULong clientId) {
		lenient().when(tokenService.evictTokensOfUser(any())).thenReturn(Mono.just(1));
		lenient().when(userSubOrgService.evictOwnerCache(any(), any())).thenReturn(Mono.just(true));
	}

	// ============================================================
	// FindUserNClient tests
	// ============================================================

	@Nested
	class FindUserNClientTests {

		@Test
		void findNonDeletedUserNClient_DelegatesToFindUserNClient_WithNonDeletedStatuses() {

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			user.setAuthorities(List.of("Authorities.Logged_IN"));

			when(dao.getUsersBy(anyString(), any(), anyString(), anyString(), any(),
					isNull(), any(SecurityUserStatusCode[].class)))
					.thenReturn(Mono.just(List.of(user)));

			when(roleService.getRoleAuthoritiesPerApp(any()))
					.thenReturn(Mono.just(Map.of(APP_CODE, List.of("Authorities.User_READ"))));

			when(profileService.getProfileAuthorities(anyString(), any(), any()))
					.thenReturn(Mono.just(List.of("Authorities.Profile_READ")));

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, CLIENT_CODE);
			when(clientService.getActiveClient(any())).thenReturn(Mono.just(client));

			StepVerifier.create(service.findNonDeletedUserNClient(
					"testuser", null, CLIENT_CODE, APP_CODE, AuthenticationIdentifierType.USER_NAME))
					.assertNext(tuple -> {
						assertNotNull(tuple.getT1());
						assertNotNull(tuple.getT2());
						assertNotNull(tuple.getT3());
						assertEquals(CLIENT_CODE, tuple.getT2().getCode());
					})
					.verifyComplete();
		}

		@Test
		void findNonDeletedUserNActiveClient_DelegatesToFindUserNClient_WithActiveClientStatus() {

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			user.setAuthorities(List.of("Authorities.Logged_IN"));

			when(dao.getUsersBy(anyString(), any(), anyString(), anyString(), any(),
					eq(com.fincity.security.jooq.enums.SecurityClientStatusCode.ACTIVE),
					any(SecurityUserStatusCode[].class)))
					.thenReturn(Mono.just(List.of(user)));

			when(roleService.getRoleAuthoritiesPerApp(any()))
					.thenReturn(Mono.just(Map.of(APP_CODE, List.of("Authorities.User_READ"))));

			when(profileService.getProfileAuthorities(anyString(), any(), any()))
					.thenReturn(Mono.just(List.of("Authorities.Profile_READ")));

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, CLIENT_CODE);
			when(clientService.getActiveClient(any())).thenReturn(Mono.just(client));

			StepVerifier.create(service.findNonDeletedUserNActiveClient(
					"testuser", null, CLIENT_CODE, APP_CODE, AuthenticationIdentifierType.USER_NAME))
					.assertNext(tuple -> {
						assertNotNull(tuple.getT3());
						assertEquals(USER_ID, tuple.getT3().getId());
					})
					.verifyComplete();
		}

		@Test
		void findUserNClient_NoUserFound_ReturnsEmpty() {

			when(dao.getUsersBy(anyString(), any(), anyString(), anyString(), any(),
					any(), any(SecurityUserStatusCode[].class)))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.findUserNClient(
					"unknown", null, CLIENT_CODE, APP_CODE,
					AuthenticationIdentifierType.USER_NAME, null,
					SecurityUserStatusCode.ACTIVE))
					.verifyComplete();
		}

		@Test
		void findUserNClient_MultipleUsersFound_ReturnsEmpty() {

			User user1 = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			User user2 = TestDataFactory.createActiveUser(OTHER_USER_ID, BUS_CLIENT_ID);

			when(dao.getUsersBy(anyString(), any(), anyString(), anyString(), any(),
					any(), any(SecurityUserStatusCode[].class)))
					.thenReturn(Mono.just(List.of(user1, user2)));

			StepVerifier.create(service.findUserNClient(
					"testuser", null, CLIENT_CODE, APP_CODE,
					AuthenticationIdentifierType.USER_NAME, null,
					SecurityUserStatusCode.ACTIVE))
					.verifyComplete();
		}

		@Test
		void findUserNClient_DifferentClientCode_FetchesBothClients() {

			String loggedInClientCode = "LOGGEDIN";

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			user.setAuthorities(List.of("Authorities.Logged_IN"));

			when(dao.getUsersBy(anyString(), any(), anyString(), anyString(), any(),
					any(), any(SecurityUserStatusCode[].class)))
					.thenReturn(Mono.just(List.of(user)));

			when(roleService.getRoleAuthoritiesPerApp(any()))
					.thenReturn(Mono.just(Map.of()));

			when(profileService.getProfileAuthorities(anyString(), any(), any()))
					.thenReturn(Mono.just(List.of()));

			Client userClient = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, CLIENT_CODE);
			when(clientService.getActiveClient(any())).thenReturn(Mono.just(userClient));

			Client loggedInClient = TestDataFactory.createBusinessClient(ULong.valueOf(5), loggedInClientCode);
			when(clientService.getClientBy(eq(loggedInClientCode))).thenReturn(Mono.just(loggedInClient));

			StepVerifier.create(service.findUserNClient(
					"testuser", null, loggedInClientCode, APP_CODE,
					AuthenticationIdentifierType.USER_NAME, null,
					SecurityUserStatusCode.ACTIVE))
					.assertNext(tuple -> {
						assertEquals(loggedInClientCode, tuple.getT1().getCode());
						assertEquals(CLIENT_CODE, tuple.getT2().getCode());
					})
					.verifyComplete();
		}
	}

	// ============================================================
	// GetUserAuthorities tests
	// ============================================================

	@Nested
	class GetUserAuthoritiesTests {

		@Test
		void getUserAuthorities_CombinesRoleAndProfileAuthorities() {

			when(roleService.getRoleAuthoritiesPerApp(any()))
					.thenReturn(Mono.just(Map.of(APP_CODE, List.of("Authorities.User_READ", "Authorities.User_CREATE"))));

			when(profileService.getProfileAuthorities(eq(APP_CODE), any(), any()))
					.thenReturn(Mono.just(List.of("Authorities.Profile_READ")));

			StepVerifier.create(service.getUserAuthorities(APP_CODE, BUS_CLIENT_ID, USER_ID))
					.assertNext(auths -> {
						assertTrue(auths.contains("Authorities.User_READ"));
						assertTrue(auths.contains("Authorities.User_CREATE"));
						assertTrue(auths.contains("Authorities.Profile_READ"));
						assertTrue(auths.contains("Authorities.Logged_IN"));
					})
					.verifyComplete();
		}

		@Test
		void getUserAuthorities_NoAppSpecificRoles_UsesDefaultRoles() {

			when(roleService.getRoleAuthoritiesPerApp(any()))
					.thenReturn(Mono.just(Map.of("", new ArrayList<>(List.of("Authorities.Default_Auth")))));

			when(profileService.getProfileAuthorities(eq(APP_CODE), any(), any()))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.getUserAuthorities(APP_CODE, BUS_CLIENT_ID, USER_ID))
					.assertNext(auths -> {
						assertTrue(auths.contains("Authorities.Default_Auth"));
						assertTrue(auths.contains("Authorities.Logged_IN"));
					})
					.verifyComplete();
		}

		@Test
		void getUserAuthorities_BothAppAndDefaultRoles_MergesThem() {

			Map<String, List<String>> roleMap = Map.of(
					APP_CODE, List.of("Authorities.App_Auth"),
					"", List.of("Authorities.Default_Auth"));

			when(roleService.getRoleAuthoritiesPerApp(any()))
					.thenReturn(Mono.just(roleMap));

			when(profileService.getProfileAuthorities(eq(APP_CODE), any(), any()))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.getUserAuthorities(APP_CODE, BUS_CLIENT_ID, USER_ID))
					.assertNext(auths -> {
						assertTrue(auths.contains("Authorities.App_Auth"));
						assertTrue(auths.contains("Authorities.Default_Auth"));
						assertTrue(auths.contains("Authorities.Logged_IN"));
					})
					.verifyComplete();
		}
	}

	// ============================================================
	// CheckUserAndClient tests
	// ============================================================

	@Nested
	class CheckUserAndClientTests {

		@Test
		void checkUserAndClient_NullClientCode_ReturnsFalse() {

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.checkUserAndClient(
					Tuples.of(client, client, user), null))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void checkUserAndClient_SystemClient_ReturnsTrue() {

			Client systemClient = TestDataFactory.createSystemClient();
			Client userClient = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.checkUserAndClient(
					Tuples.of(systemClient, userClient, user), "ANYCODE"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void checkUserAndClient_MatchingCode_ReturnsTrue() {

			Client client = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.checkUserAndClient(
					Tuples.of(client, client, user), CLIENT_CODE))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void checkUserAndClient_SameClientIds_ReturnsTrue() {

			Client loginClient = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, "LOGIN");
			Client userClient = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.checkUserAndClient(
					Tuples.of(loginClient, userClient, user), CLIENT_CODE))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void checkUserAndClient_DifferentClientCodeAndIds_ReturnsFalse() {

			Client loginClient = TestDataFactory.createBusinessClient(ULong.valueOf(5), "OTHER");
			Client userClient = TestDataFactory.createBusinessClient(BUS_CLIENT_ID, CLIENT_CODE);
			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.checkUserAndClient(
					Tuples.of(loginClient, userClient, user), "MISMATCH"))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// CheckUserStatus tests
	// ============================================================

	@Nested
	class CheckUserStatusTests {

		@Test
		void checkUserStatus_ActiveUserInActiveList_ReturnsTrue() {

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.checkUserStatus(user, SecurityUserStatusCode.ACTIVE))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void checkUserStatus_ActiveUserNotInList_ReturnsFalse() {

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.checkUserStatus(user,
					SecurityUserStatusCode.LOCKED, SecurityUserStatusCode.INACTIVE))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void checkUserStatus_NullUser_ReturnsFalse() {

			StepVerifier.create(service.checkUserStatus(null, SecurityUserStatusCode.ACTIVE))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void checkUserStatus_EmptyStatusCodes_ReturnsFalse() {

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.checkUserStatus(user))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void checkUserStatus_LockedUserInLockedList_ReturnsTrue() {

			User user = TestDataFactory.createLockedUser(USER_ID, BUS_CLIENT_ID,
					LocalDateTime.now().plusMinutes(10));

			StepVerifier.create(service.checkUserStatus(user, SecurityUserStatusCode.LOCKED))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// Read tests
	// ============================================================

	@Nested
	class ReadTests {

		@Test
		void read_OwnUser_ReturnsUser() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			User user = TestDataFactory.createActiveUser(ULong.valueOf(10), BUS_CLIENT_ID);
			when(dao.readById(ULong.valueOf(10))).thenReturn(Mono.just(user));

			StepVerifier.create(service.read(ULong.valueOf(10)))
					.assertNext(u -> assertEquals(ULong.valueOf(10), u.getId()))
					.verifyComplete();
		}

		@Test
		void read_OtherUserWithAuthority_ReturnsUser() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.User_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			securityContextMock.when(() -> SecurityContextUtil.hasAuthority(anyString(),
						ArgumentMatchers.<List<String>>any()))
					.thenReturn(true);

			User user = TestDataFactory.createActiveUser(OTHER_USER_ID, BUS_CLIENT_ID);
			when(dao.readById(OTHER_USER_ID)).thenReturn(Mono.just(user));

			StepVerifier.create(service.read(OTHER_USER_ID))
					.assertNext(u -> assertEquals(OTHER_USER_ID, u.getId()))
					.verifyComplete();
		}

		@Test
		void read_OtherUserWithoutAuthority_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			securityContextMock.when(() -> SecurityContextUtil.hasAuthority(anyString(),
						ArgumentMatchers.<List<String>>any()))
					.thenReturn(false);

			User user = TestDataFactory.createActiveUser(OTHER_USER_ID, BUS_CLIENT_ID);
			when(dao.readById(OTHER_USER_ID)).thenReturn(Mono.just(user));

			StepVerifier.create(service.read(OTHER_USER_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void read_UserNotFound_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			when(dao.readById(OTHER_USER_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.read(OTHER_USER_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// ReadInternal tests
	// ============================================================

	@Nested
	class ReadInternalTests {

		@Test
		void readInternal_UsesCache() {

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));

			StepVerifier.create(service.readInternal(USER_ID))
					.assertNext(u -> assertEquals(USER_ID, u.getId()))
					.verifyComplete();
		}
	}

	// ============================================================
	// Delete tests
	// ============================================================

	@Nested
	class DeleteTests {

		@Test
		void delete_HasAccess_SetsDeletedStatusAndEvictsCache() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			securityContextMock.when(() -> SecurityContextUtil.hasAuthority(anyString(),
						ArgumentMatchers.<List<String>>any()))
					.thenReturn(true);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), any(ULong.class)))
					.thenReturn(Mono.just(true));

			when(clientService.getClientTypeNCodeNClientLevel(BUS_CLIENT_ID))
					.thenReturn(Mono.just(Tuples.of("BUS", CLIENT_CODE, "CLIENT")));
			when(dao.checkUserExists(any(), any(), any(), any(), any())).thenReturn(Mono.just(false));
			when(dao.canBeUpdated(USER_ID)).thenReturn(Mono.just(true));

			when(dao.update(any(User.class))).thenReturn(Mono.just(user));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.delete(USER_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void delete_NoAccess_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_DELETE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			securityContextMock.when(() -> SecurityContextUtil.hasAuthority(anyString(),
						ArgumentMatchers.<List<String>>any()))
					.thenReturn(true);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), any(ULong.class)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.delete(USER_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// AssignRoleToUser tests
	// ============================================================

	@Nested
	class AssignRoleToUserTests {

		@Test
		void assignRoleToUser_AlreadyAssigned_ReturnsTrue() {

			when(dao.checkRoleAssignedForUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.assignRoleToUser(USER_ID, ROLE_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void assignRoleToUser_SystemClient_AssignsRole() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.checkRoleAssignedForUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(false));

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(profileService.hasAccessToRoles(eq(BUS_CLIENT_ID), eq(Set.of(ROLE_ID))))
					.thenReturn(Mono.just(true));

			when(dao.addRoleToUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(true));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.assignRoleToUser(USER_ID, ROLE_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void assignRoleToUser_ManagedClient_AssignsRole() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.User_UPDATE", "Authorities.Role_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(dao.checkRoleAssignedForUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(false));

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(profileService.hasAccessToRoles(eq(BUS_CLIENT_ID), eq(Set.of(ROLE_ID))))
					.thenReturn(Mono.just(true));

			when(dao.addRoleToUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(true));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.assignRoleToUser(USER_ID, ROLE_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void assignRoleToUser_NotManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_UPDATE", "Authorities.Role_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(dao.checkRoleAssignedForUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(false));

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.assignRoleToUser(USER_ID, ROLE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void assignRoleToUser_NoAccessToRole_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.checkRoleAssignedForUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(false));

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(profileService.hasAccessToRoles(eq(BUS_CLIENT_ID), eq(Set.of(ROLE_ID))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.assignRoleToUser(USER_ID, ROLE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// RemoveRoleFromUser tests
	// ============================================================

	@Nested
	class RemoveRoleFromUserTests {

		@Test
		void removeRoleFromUser_SystemClient_RemovesRole() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(dao.removeRoleForUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(1));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.removeRoleFromUser(USER_ID, ROLE_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void removeRoleFromUser_ManagedClient_RemovesRole() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.User_UPDATE", "Authorities.Role_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(dao.removeRoleForUser(USER_ID, ROLE_ID)).thenReturn(Mono.just(1));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.removeRoleFromUser(USER_ID, ROLE_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void removeRoleFromUser_NotManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_UPDATE", "Authorities.Role_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.removeRoleFromUser(USER_ID, ROLE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// AssignProfileToUser tests
	// ============================================================

	@Nested
	class AssignProfileToUserTests {

		@Test
		void assignProfileToUser_ManagedClient_AssignsProfile() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(profileService.hasAccessToProfiles(eq(BUS_CLIENT_ID), eq(Set.of(PROFILE_ID))))
					.thenReturn(Mono.just(true));

			when(dao.addProfileToUser(USER_ID, PROFILE_ID)).thenReturn(Mono.just(1));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.assignProfileToUser(USER_ID, PROFILE_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void assignProfileToUser_NotManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_UPDATE", "Authorities.Profile_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.assignProfileToUser(USER_ID, PROFILE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void assignProfileToUser_NoAccessToProfile_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(profileService.hasAccessToProfiles(eq(BUS_CLIENT_ID), eq(Set.of(PROFILE_ID))))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.assignProfileToUser(USER_ID, PROFILE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// RemoveProfileFromUser tests
	// ============================================================

	@Nested
	class RemoveProfileFromUserTests {

		@Test
		void removeProfileFromUser_ManagedClient_RemovesProfile() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(dao.removeProfileForUser(USER_ID, PROFILE_ID)).thenReturn(Mono.just(1));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.removeProfileFromUser(USER_ID, PROFILE_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void removeProfileFromUser_NotManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_UPDATE", "Authorities.Profile_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.removeProfileFromUser(USER_ID, PROFILE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// MakeUserActive tests
	// ============================================================

	@Nested
	class MakeUserActiveTests {

		@Test
		void makeUserActive_SystemClient_ManagedUser_Succeeds() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createInactiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));
			when(dao.update(any(User.class))).thenReturn(Mono.just(user));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.makeUserActive(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void makeUserActive_AlreadyActive_ReturnsTrue() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.makeUserActive(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void makeUserActive_NotManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createInactiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.makeUserActive(USER_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void makeUserActive_NullUserId_UsesLoggedInUser() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createInactiveUser(ULong.valueOf(1), SYSTEM_CLIENT_ID);
			when(dao.readInternal(ULong.valueOf(1))).thenReturn(Mono.just(user));
			when(dao.readById(ULong.valueOf(1))).thenReturn(Mono.just(user));
			when(dao.update(any(User.class))).thenReturn(Mono.just(user));

			setupEvictCacheMocks(ULong.valueOf(1), SYSTEM_CLIENT_ID);

			StepVerifier.create(service.makeUserActive(null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// MakeUserInActive tests
	// ============================================================

	@Nested
	class MakeUserInActiveTests {

		@Test
		void makeUserInActive_SystemClient_ManagedUser_Succeeds() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));
			when(dao.update(any(User.class))).thenReturn(Mono.just(user));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.makeUserInActive(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void makeUserInActive_AlreadyInactive_ReturnsTrue() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createInactiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.makeUserInActive(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void makeUserInActive_NotManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.makeUserInActive(USER_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void makeUserInActive_NullUserId_UsesLoggedInUser() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(ULong.valueOf(1), SYSTEM_CLIENT_ID);
			when(dao.readInternal(ULong.valueOf(1))).thenReturn(Mono.just(user));
			when(dao.readById(ULong.valueOf(1))).thenReturn(Mono.just(user));
			when(dao.update(any(User.class))).thenReturn(Mono.just(user));

			setupEvictCacheMocks(ULong.valueOf(1), SYSTEM_CLIENT_ID);

			StepVerifier.create(service.makeUserInActive(null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// UnblockUser tests
	// ============================================================

	@Nested
	class UnblockUserTests {

		@Test
		void unblockUser_SystemClient_Succeeds() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.updateUserStatusToActive(USER_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.unblockUser(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void unblockUser_ManagedClient_Succeeds() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createLockedUser(USER_ID, BUS_CLIENT_ID,
					LocalDateTime.now().plusMinutes(10));
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(dao.updateUserStatusToActive(USER_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.unblockUser(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void unblockUser_NotManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createLockedUser(USER_ID, BUS_CLIENT_ID,
					LocalDateTime.now().plusMinutes(10));
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.unblockUser(USER_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void unblockUser_NullUserId_UsesLoggedInUser() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.updateUserStatusToActive(ULong.valueOf(1))).thenReturn(Mono.just(true));

			StepVerifier.create(service.unblockUser(null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// LockUserInternal / UnlockUserInternal tests
	// ============================================================

	@Nested
	class LockUnlockInternalTests {

		@Test
		void lockUserInternal_DelegatesToDao() {

			LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(15);
			when(dao.lockUser(USER_ID, lockUntil, "Too many attempts"))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.lockUserInternal(USER_ID, lockUntil, "Too many attempts"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void unlockUserInternal_DelegatesToDao() {

			when(dao.updateUserStatusToActive(USER_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.unlockUserInternal(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// IncreaseFailedAttempt / ResetFailedAttempt tests
	// ============================================================

	@Nested
	class FailedAttemptTests {

		@Test
		void increaseFailedAttempt_DelegatesToDao() {

			when(dao.increaseFailedAttempt(USER_ID, AuthenticationPasswordType.PASSWORD))
					.thenReturn(Mono.just((short) 1));

			StepVerifier.create(service.increaseFailedAttempt(USER_ID, AuthenticationPasswordType.PASSWORD))
					.assertNext(result -> assertEquals((short) 1, result))
					.verifyComplete();
		}

		@Test
		void increaseFailedAttempt_Pin_DelegatesToDao() {

			when(dao.increaseFailedAttempt(USER_ID, AuthenticationPasswordType.PIN))
					.thenReturn(Mono.just((short) 2));

			StepVerifier.create(service.increaseFailedAttempt(USER_ID, AuthenticationPasswordType.PIN))
					.assertNext(result -> assertEquals((short) 2, result))
					.verifyComplete();
		}

		@Test
		void resetFailedAttempt_DelegatesToDao() {

			when(dao.resetFailedAttempt(USER_ID, AuthenticationPasswordType.PASSWORD))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.resetFailedAttempt(USER_ID, AuthenticationPasswordType.PASSWORD))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void resetFailedAttempt_Otp_DelegatesToDao() {

			when(dao.resetFailedAttempt(USER_ID, AuthenticationPasswordType.OTP))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.resetFailedAttempt(USER_ID, AuthenticationPasswordType.OTP))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// CheckUserExistsAcrossApps tests
	// ============================================================

	@Nested
	class CheckUserExistsAcrossAppsTests {

		@Test
		void checkUserExistsAcrossApps_AllNullParams_ThrowsBadRequest() {

			StepVerifier.create(service.checkUserExistsAcrossApps(null, null, null))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void checkUserExistsAcrossApps_WithUserName_DelegatesToDao() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(dao.checkUserExists(any(ULong.class), eq("testuser"), isNull(), isNull(), isNull()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.checkUserExistsAcrossApps("testuser", null, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void checkUserExistsAcrossApps_WithEmail_DelegatesToDao() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(dao.checkUserExists(any(ULong.class), isNull(), eq("test@test.com"), isNull(), isNull()))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.checkUserExistsAcrossApps(null, "test@test.com", null))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void checkUserExistsAcrossApps_WithPhoneNumber_DelegatesToDao() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			setupSecurityContext(ca);

			when(dao.checkUserExists(any(ULong.class), isNull(), isNull(), eq("+1234567890"), isNull()))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.checkUserExistsAcrossApps(null, null, "+1234567890"))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// UpdateDesignation tests
	// ============================================================

	@Nested
	class UpdateDesignationTests {

		@Test
		void updateDesignation_SystemClient_HappyPath() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(designationService.canAssignDesignation(BUS_CLIENT_ID, DESIGNATION_ID))
					.thenReturn(Mono.just(true));

			when(dao.update(any(User.class))).thenReturn(Mono.just(user));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.updateDesignation(USER_ID, DESIGNATION_ID))
					.assertNext(u -> assertNotNull(u))
					.verifyComplete();
		}

		@Test
		void updateDesignation_NotManaged_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "OTHER",
					List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.updateDesignation(USER_ID, DESIGNATION_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void updateDesignation_CannotAssign_ThrowsBadRequest() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(designationService.canAssignDesignation(BUS_CLIENT_ID, DESIGNATION_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.updateDesignation(USER_ID, DESIGNATION_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void updateDesignation_ManagedClient_HappyPath() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(designationService.canAssignDesignation(BUS_CLIENT_ID, DESIGNATION_ID))
					.thenReturn(Mono.just(true));

			when(dao.update(any(User.class))).thenReturn(Mono.just(user));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.updateDesignation(USER_ID, DESIGNATION_ID))
					.assertNext(u -> assertNotNull(u))
					.verifyComplete();
		}
	}

	// ============================================================
	// CheckIfUserIsOwner tests
	// ============================================================

	@Nested
	class CheckIfUserIsOwnerTests {

		@Test
		void checkIfUserIsOwner_NullUserId_ReturnsEmpty() {

			StepVerifier.create(service.checkIfUserIsOwner(null))
					.verifyComplete();
		}

		@Test
		void checkIfUserIsOwner_ValidUser_DelegatesToDao() {

			when(dao.checkIfUserIsOwner(USER_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.checkIfUserIsOwner(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void checkIfUserIsOwner_NonOwner_ReturnsFalse() {

			when(dao.checkIfUserIsOwner(USER_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.checkIfUserIsOwner(USER_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// GetNonDeletedUserStatusCodes tests
	// ============================================================

	@Nested
	class GetNonDeletedUserStatusCodesTests {

		@Test
		void getNonDeletedUserStatusCodes_ReturnsAllExceptDeleted() {

			SecurityUserStatusCode[] codes = service.getNonDeletedUserStatusCodes();

			assertEquals(4, codes.length);
			List<SecurityUserStatusCode> codeList = List.of(codes);
			assertTrue(codeList.contains(SecurityUserStatusCode.ACTIVE));
			assertTrue(codeList.contains(SecurityUserStatusCode.INACTIVE));
			assertTrue(codeList.contains(SecurityUserStatusCode.LOCKED));
			assertTrue(codeList.contains(SecurityUserStatusCode.PASSWORD_EXPIRED));
			assertFalse(codeList.contains(SecurityUserStatusCode.DELETED));
		}
	}

	// ============================================================
	// UpdatePassword tests
	// ============================================================

	@Nested
	class UpdatePasswordTests {

		@Test
		void updatePassword_NotAuthenticated_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			ca.setAuthenticated(false);
			setupSecurityContext(ca);

			RequestUpdatePassword reqPassword = TestDataFactory.createRequestUpdatePassword("oldPass", "newPass123!");
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);

			StepVerifier.create(service.updatePassword(USER_ID, reqPassword))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void updatePassword_BlankNewPassword_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));

			RequestUpdatePassword reqPassword = TestDataFactory.createRequestUpdatePassword("oldPass", "");
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);

			StepVerifier.create(service.updatePassword(USER_ID, reqPassword))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void updatePassword_NullNewPassword_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readInternal(USER_ID)).thenReturn(Mono.just(user));

			RequestUpdatePassword reqPassword = TestDataFactory.createRequestUpdatePassword("oldPass", null);
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);

			StepVerifier.create(service.updatePassword(USER_ID, reqPassword))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void updatePassword_SameUser_ChecksOldPasswordEquality() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			securityContextMock.when(() -> SecurityContextUtil.hasAuthority(anyString()))
					.thenReturn(Mono.just(true));

			User user = TestDataFactory.createActiveUser(ULong.valueOf(10), BUS_CLIENT_ID);
			user.setPassword("hashedOldPassword");
			user.setPasswordHashed(true);
			when(dao.readInternal(ULong.valueOf(10))).thenReturn(Mono.just(user));

			when(passwordEncoder.matches(eq("10oldPass"), eq("hashedOldPassword"))).thenReturn(true);
			when(passwordEncoder.matches(eq("10newPass123!"), eq("hashedOldPassword"))).thenReturn(false);

			when(clientService.validatePasswordPolicy(nullable(ULong.class), nullable(String.class), nullable(ULong.class),
					nullable(AuthenticationPasswordType.class), nullable(String.class)))
					.thenReturn(Mono.just(true));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), any(ULong.class)))
					.thenReturn(Mono.just(true));

			when(dao.setPassword(any(), any(), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(1));

			when(ecService.createEvent(any())).thenReturn(Mono.just(true));
			when(dao.updateUserStatusToActive(any())).thenReturn(Mono.just(true));

			setupEvictCacheMocks(ULong.valueOf(10), BUS_CLIENT_ID);

			RequestUpdatePassword reqPassword = TestDataFactory.createRequestUpdatePassword("oldPass", "newPass123!");
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);

			StepVerifier.create(service.updatePassword(ULong.valueOf(10), reqPassword))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void updatePassword_LoggedInUser_WithNoExplicitUserId() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(ULong.valueOf(1), SYSTEM_CLIENT_ID);
			user.setPassword("hashedOldPassword");
			user.setPasswordHashed(true);
			when(dao.readInternal(ULong.valueOf(1))).thenReturn(Mono.just(user));

			when(passwordEncoder.matches(eq("1oldPass"), eq("hashedOldPassword"))).thenReturn(true);
			when(passwordEncoder.matches(eq("1newPass123!"), eq("hashedOldPassword"))).thenReturn(false);

			when(clientService.validatePasswordPolicy(nullable(ULong.class), nullable(String.class), nullable(ULong.class),
					nullable(AuthenticationPasswordType.class), nullable(String.class)))
					.thenReturn(Mono.just(true));

			when(dao.setPassword(any(), any(), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(1));

			when(ecService.createEvent(any())).thenReturn(Mono.just(true));
			when(dao.updateUserStatusToActive(any())).thenReturn(Mono.just(true));

			setupEvictCacheMocks(ULong.valueOf(1), SYSTEM_CLIENT_ID);

			RequestUpdatePassword reqPassword = TestDataFactory.createRequestUpdatePassword("oldPass", "newPass123!");
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);

			StepVerifier.create(service.updatePassword(reqPassword))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// GenerateOtpResetPassword tests
	// ============================================================

	@Nested
	class GenerateOtpResetPasswordTests {

		@Test
		void generateOtpResetPassword_AlreadyAuthenticated_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			ca.setAuthenticated(true);
			setupSecurityContext(ca);

			com.fincity.security.model.AuthenticationRequest authRequest = new com.fincity.security.model.AuthenticationRequest();
			authRequest.setUserName("testuser");

			org.springframework.http.server.reactive.ServerHttpRequest request = mock(
					org.springframework.http.server.reactive.ServerHttpRequest.class);

			StepVerifier.create(service.generateOtpResetPassword(authRequest, request))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// VerifyOtpResetPassword tests
	// ============================================================

	@Nested
	class VerifyOtpResetPasswordTests {

		@Test
		void verifyOtpResetPassword_AlreadyAuthenticated_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			ca.setAuthenticated(true);
			setupSecurityContext(ca);

			com.fincity.security.model.AuthenticationRequest authRequest = new com.fincity.security.model.AuthenticationRequest();
			authRequest.setUserName("testuser");
			authRequest.setOtp("1234");

			StepVerifier.create(service.verifyOtpResetPassword(authRequest))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// ResetPassword tests
	// ============================================================

	@Nested
	class ResetPasswordTests {

		@Test
		void resetPassword_AlreadyAuthenticated_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, CLIENT_CODE,
					List.of("Authorities.Logged_IN"));
			ca.setAuthenticated(true);
			setupSecurityContext(ca);

			com.fincity.security.model.AuthenticationRequest authRequest = new com.fincity.security.model.AuthenticationRequest();
			authRequest.setUserName("testuser");
			authRequest.setOtp("1234");

			RequestUpdatePassword reqPassword = new RequestUpdatePassword();
			reqPassword.setNewPassword("newPass123!");
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);
			reqPassword.setAuthRequest(authRequest);

			StepVerifier.create(service.resetPassword(reqPassword))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// Update (by Map) tests
	// ============================================================

	@SuppressWarnings("unchecked")
	@Nested
	class UpdateByMapTests {

		@Test
		void update_ByMap_BusClientType_NoExistingUser_UpdatesSuccessfully() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			when(dao.getUserClientId(USER_ID)).thenReturn(Mono.just(BUS_CLIENT_ID));

			when(clientService.getClientTypeNCodeNClientLevel(BUS_CLIENT_ID))
					.thenReturn(Mono.just(Tuples.of("BUS", CLIENT_CODE, "CLIENT")));

			when(dao.checkUserExists(any(), any(), any(), any(), any()))
					.thenReturn(Mono.just(false));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			User existingUser = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(existingUser));
			when(dao.canBeUpdated(USER_ID)).thenReturn(Mono.just(true));
			when(dao.update(any(User.class))).thenReturn(Mono.just(existingUser));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			Map<String, Object> fields = Map.of("userName", "newname");

			StepVerifier.create(service.update(USER_ID, fields))
					.assertNext(user -> assertNotNull(user))
					.verifyComplete();
		}

		@Test
		void update_ByMap_DuplicateUser_ThrowsForbidden() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(dao.getUserClientId(USER_ID)).thenReturn(Mono.just(BUS_CLIENT_ID));

			when(clientService.getClientTypeNCodeNClientLevel(BUS_CLIENT_ID))
					.thenReturn(Mono.just(Tuples.of("BUS", CLIENT_CODE, "CLIENT")));

			when(dao.checkUserExists(eq(BUS_CLIENT_ID), anyString(), isNull(), isNull(), isNull()))
					.thenReturn(Mono.just(true));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			User updatedUser = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.update(eq(USER_ID), any(Map.class))).thenReturn(Mono.just(updatedUser));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			Map<String, Object> fields = Map.of("userName", "existinguser");

			StepVerifier.create(service.update(USER_ID, fields))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	// ============================================================
	// Update (by Entity) tests
	// ============================================================

	@Nested
	class UpdateByEntityTests {

		@Test
		void update_ByEntity_BusClientType_NoExistingUser_UpdatesSuccessfully() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			securityContextMock.when(() -> SecurityContextUtil.hasAuthority(anyString(),
						ArgumentMatchers.<List<String>>any()))
					.thenReturn(true);

			User entity = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			entity.setUserName("updateduser");
			entity.setEmailId("updated@test.com");

			when(clientService.getClientTypeNCodeNClientLevel(BUS_CLIENT_ID))
					.thenReturn(Mono.just(Tuples.of("BUS", CLIENT_CODE, "CLIENT")));

			when(dao.checkUserExists(eq(BUS_CLIENT_ID), eq("updateduser"), eq("updated@test.com"),
					isNull(), isNull()))
					.thenReturn(Mono.just(false));

			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(dao.readById(USER_ID)).thenReturn(Mono.just(entity));
			when(dao.update(any(User.class))).thenReturn(Mono.just(entity));

			setupEvictCacheMocks(USER_ID, BUS_CLIENT_ID);

			StepVerifier.create(service.update(entity))
					.assertNext(user -> assertEquals("updateduser", user.getUserName()))
					.verifyComplete();
		}
	}

	// ============================================================
	// Create tests
	// ============================================================

	@Nested
	class CreateTests {

		@Test
		void create_SystemClient_SkipsAccessCheck() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			User entity = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			entity.setUserName("newuser");
			entity.setEmailId("new@test.com");
			entity.setPhoneNumber("+1234567890");

			when(clientService.getClientTypeNCodeNClientLevel(BUS_CLIENT_ID))
					.thenReturn(Mono.just(Tuples.of("BUS", CLIENT_CODE, "CLIENT")));

			when(clientService.validatePasswordPolicy(any(ULong.class), anyString(), any(ULong.class),
					any(AuthenticationPasswordType.class), anyString()))
					.thenReturn(Mono.just(true));

			when(dao.checkUserExists(eq(BUS_CLIENT_ID), eq("BUS"), eq("newuser"),
					eq("new@test.com"), eq("+1234567890")))
					.thenReturn(Mono.just(false));

			when(dao.create(any(User.class))).thenReturn(Mono.just(entity));

			when(dao.setPassword(any(), any(), anyString(), any(AuthenticationPasswordType.class)))
					.thenReturn(Mono.just(1));

			when(userSubOrgService.evictOwnerCache(any(), any())).thenReturn(Mono.just(true));

			StepVerifier.create(service.create(entity))
					.assertNext(user -> assertEquals("newuser", user.getUserName()))
					.verifyComplete();
		}

		@Test
		void create_SetsPlaceholderForBlankIdentificationKeys() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			User entity = new User();
			entity.setClientId(BUS_CLIENT_ID);
			entity.setUserName("");
			entity.setEmailId("");
			entity.setPhoneNumber("");
			entity.setFirstName("Test");
			entity.setLastName("User");
			entity.setStatusCode(SecurityUserStatusCode.ACTIVE);

			// All three identification keys are blank/placeholder -> should fail
			StepVerifier.create(service.create(entity))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void create_NullClientId_SetsFromContextAuthentication() {

			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			securityContextMock.when(SecurityContextUtil::getUsersContextUser)
					.thenReturn(Mono.just(ca.getUser()));

			User entity = TestDataFactory.createActiveUser(USER_ID, null);
			entity.setUserName("newuser");
			entity.setEmailId("new@test.com");
			entity.setPhoneNumber("+1234567890");

			when(clientService.getClientTypeNCodeNClientLevel(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(Tuples.of("SYS", "SYSTEM", "SYSTEM")));

			when(dao.checkUserExists(any(), any(), any(), any(), any())).thenReturn(Mono.just(false));

			when(dao.create(any(User.class))).thenReturn(Mono.just(entity));

			when(userSubOrgService.evictOwnerCache(any(), any())).thenReturn(Mono.just(true));

			StepVerifier.create(service.create(entity))
					.assertNext(user -> assertNotNull(user))
					.verifyComplete();
		}
	}

	// ============================================================
	// IncreaseResendAttempt / ResetResendAttempt tests
	// ============================================================

	@Nested
	class ResendAttemptTests {

		@Test
		void increaseResendAttempt_DelegatesToDao() {

			when(dao.increaseResendAttempts(USER_ID)).thenReturn(Mono.just((short) 1));

			StepVerifier.create(service.increaseResendAttempt(USER_ID))
					.assertNext(result -> assertEquals((short) 1, result))
					.verifyComplete();
		}

		@Test
		void resetResendAttempt_DelegatesToDao() {

			when(dao.resetResendAttempts(USER_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.resetResendAttempt(USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// ============================================================
	// GetUserForContext tests
	// ============================================================

	@Nested
	class GetUserForContextTests {

		@Test
		void getUserForContext_ReturnsUserWithAuthorities() {

			User user = TestDataFactory.createActiveUser(USER_ID, BUS_CLIENT_ID);
			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			when(roleService.getRoleAuthoritiesPerApp(any()))
					.thenReturn(Mono.just(Map.of(APP_CODE, List.of("Authorities.User_READ"))));

			when(profileService.getProfileAuthorities(eq(APP_CODE), any(), any()))
					.thenReturn(Mono.just(List.of()));

			StepVerifier.create(service.getUserForContext(APP_CODE, USER_ID))
					.assertNext(u -> {
						assertEquals(USER_ID, u.getId());
						assertNotNull(u.getAuthorities());
					})
					.verifyComplete();
		}
	}

	// ============================================================
	// GetSoxObjectName tests
	// ============================================================

	@Nested
	class GetSoxObjectNameTests {

		@Test
		void getSoxObjectName_ReturnsUser() {

			assertEquals(com.fincity.security.jooq.enums.SecuritySoxLogObjectName.USER,
					service.getSoxObjectName());
		}
	}
}
