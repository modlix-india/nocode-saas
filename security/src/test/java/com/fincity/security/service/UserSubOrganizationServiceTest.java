package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

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
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.User;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UserSubOrganizationServiceTest extends AbstractServiceUnitTest {

	@Mock
	private SecurityMessageResourceService msgService;

	@Mock
	private CacheService cacheService;

	@Mock
	private TokenService tokenService;

	@Mock
	private ClientService clientService;

	@Mock
	private UserService userService;

	@Mock
	private UserDAO dao;

	@Mock
	private SoxLogService soxLogService;

	private UserSubOrganizationService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong USER_ID = ULong.valueOf(10);
	private static final ULong MANAGER_ID = ULong.valueOf(20);

	@BeforeEach
	void setUp() {
		service = new UserSubOrganizationService(msgService, cacheService, tokenService);

		// Inject dao via reflection
		var daoField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "dao");
		if (daoField == null) {
			// Search through the hierarchy
			Class<?> clazz = service.getClass().getSuperclass();
			while (clazz != null && daoField == null) {
				daoField = org.springframework.util.ReflectionUtils.findField(clazz, "dao");
				clazz = clazz.getSuperclass();
			}
		}
		assertNotNull(daoField, "dao field must exist in class hierarchy");
		daoField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(daoField, service, dao);

		// Inject clientService (normally @Lazy @Autowired via setter)
		var clientServiceField = org.springframework.util.ReflectionUtils.findField(
				UserSubOrganizationService.class, "clientService");
		clientServiceField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(clientServiceField, service, clientService);

		// Inject userService (normally @Lazy @Autowired via setter)
		var userServiceField = org.springframework.util.ReflectionUtils.findField(
				UserSubOrganizationService.class, "userService");
		userServiceField.setAccessible(true);
		org.springframework.util.ReflectionUtils.setField(userServiceField, service, userService);

		// Inject soxLogService (from AbstractSecurityUpdatableDataService)
		var soxLogField = org.springframework.util.ReflectionUtils.findField(service.getClass(), "soxLogService");
		if (soxLogField == null) {
			Class<?> clazz = service.getClass().getSuperclass();
			while (clazz != null && soxLogField == null) {
				soxLogField = org.springframework.util.ReflectionUtils.findField(clazz, "soxLogService");
				clazz = clazz.getSuperclass();
			}
		}
		if (soxLogField != null) {
			soxLogField.setAccessible(true);
			org.springframework.util.ReflectionUtils.setField(soxLogField, service, soxLogService);
		}

		setupMessageResourceService(msgService);
		setupCacheService(cacheService);
		setupSoxLogService(soxLogService);
	}

	// =========================================================================
	// evictOwnerCache()
	// =========================================================================

	@Nested
	@DisplayName("evictOwnerCache()")
	class EvictOwnerCacheTests {

		@Test
		void evictOwnerCache_NullUserId_EvictsOnlyOwnerKey() {
			when(cacheService.evict(eq("userSubOrg"), anyString())).thenReturn(Mono.just(true));

			StepVerifier.create(service.evictOwnerCache(SYSTEM_CLIENT_ID, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(cacheService).evict("userSubOrg", "1:owner");
		}

		@Test
		void evictOwnerCache_WithUserId_EvictsBothKeys() {
			when(cacheService.evict(eq("userSubOrg"), eq("1:owner"))).thenReturn(Mono.just(true));
			when(cacheService.evict(eq("userSubOrg"), eq("1:10"))).thenReturn(Mono.just(true));

			StepVerifier.create(service.evictOwnerCache(SYSTEM_CLIENT_ID, USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(cacheService).evict("userSubOrg", "1:owner");
			verify(cacheService).evict("userSubOrg", "1:10");
		}
	}

	// =========================================================================
	// canReportTo()
	// =========================================================================

	@Nested
	@DisplayName("canReportTo()")
	class CanReportToTests {

		@Test
		void canReportTo_NullReportingTo_ReturnsTrue() {
			StepVerifier.create(service.canReportTo(SYSTEM_CLIENT_ID, null, USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verifyNoInteractions(dao);
		}

		@Test
		void canReportTo_ValidReportingTo_DelegatesToDao() {
			when(dao.canReportTo(SYSTEM_CLIENT_ID, MANAGER_ID, USER_ID)).thenReturn(Mono.just(true));

			StepVerifier.create(service.canReportTo(SYSTEM_CLIENT_ID, MANAGER_ID, USER_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();

			verify(dao).canReportTo(SYSTEM_CLIENT_ID, MANAGER_ID, USER_ID);
		}

		@Test
		void canReportTo_CircularReference_ReturnsFalse() {
			when(dao.canReportTo(SYSTEM_CLIENT_ID, MANAGER_ID, USER_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.canReportTo(SYSTEM_CLIENT_ID, MANAGER_ID, USER_ID))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// updateManager()
	// =========================================================================

	@Nested
	@DisplayName("updateManager()")
	class UpdateManagerTests {

		@Test
		void updateManager_SameManager_ReturnsUserUnchanged() {
			User user = TestDataFactory.createActiveUser(USER_ID, SYSTEM_CLIENT_ID);
			user.setReportingTo(MANAGER_ID);

			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));

			StepVerifier.create(service.updateManager(USER_ID, MANAGER_ID))
					.assertNext(result -> {
						assertEquals(USER_ID, result.getId());
						assertEquals(MANAGER_ID, result.getReportingTo());
					})
					.verifyComplete();

			// Should not call update since manager is the same
			verify(dao, never()).update(any(User.class));
		}

		@Test
		void updateManager_DifferentManager_NotManaged_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "BUS",
					List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, SYSTEM_CLIENT_ID);
			user.setReportingTo(ULong.valueOf(30)); // old manager

			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));
			when(clientService.isUserClientManageClient(any(ContextAuthentication.class), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.updateManager(USER_ID, MANAGER_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void updateManager_CannotReportTo_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			User user = TestDataFactory.createActiveUser(USER_ID, SYSTEM_CLIENT_ID);
			user.setReportingTo(ULong.valueOf(30));

			when(dao.readById(USER_ID)).thenReturn(Mono.just(user));
			when(dao.canReportTo(SYSTEM_CLIENT_ID, MANAGER_ID, USER_ID)).thenReturn(Mono.just(false));

			StepVerifier.create(service.updateManager(USER_ID, MANAGER_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void updateManager_UserNotFound_CompletesEmpty() {
			when(dao.readById(USER_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.updateManager(USER_ID, MANAGER_ID))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getCurrentUserSubOrg()
	// =========================================================================

	@Nested
	@DisplayName("getCurrentUserSubOrg()")
	class GetCurrentUserSubOrgTests {

		@Test
		void getCurrentUserSubOrg_OwnerRole_ReturnsAllUsers() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth(); // has ROLE_Owner
			setupSecurityContext(ca);

			List<ULong> allUserIds = List.of(ULong.valueOf(1), ULong.valueOf(2), ULong.valueOf(3));

			when(dao.getUserIdsByClientId(eq(SYSTEM_CLIENT_ID), isNull()))
					.thenReturn(Flux.fromIterable(allUserIds));

			StepVerifier.create(service.getCurrentUserSubOrg().collectList())
					.assertNext(result -> assertEquals(3, result.size()))
					.verifyComplete();
		}

		@Test
		void getCurrentUserSubOrg_NonOwner_ReturnsSubOrgHierarchy() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(ULong.valueOf(5), "BUS",
					List.of("Authorities.User_READ", "Authorities.Logged_IN"));
			setupSecurityContext(ca);

			// Non-owner: should use expandDeep to find sub-org
			List<ULong> level1 = List.of(ULong.valueOf(11), ULong.valueOf(12));

			when(dao.getLevel1SubOrg(eq(ULong.valueOf(5)), eq(ULong.valueOf(10))))
					.thenReturn(Flux.fromIterable(level1));
			when(dao.getLevel1SubOrg(eq(ULong.valueOf(5)), eq(ULong.valueOf(11))))
					.thenReturn(Flux.empty());
			when(dao.getLevel1SubOrg(eq(ULong.valueOf(5)), eq(ULong.valueOf(12))))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.getCurrentUserSubOrg().collectList())
					.assertNext(result -> {
						assertTrue(result.contains(ULong.valueOf(10))); // self
						assertTrue(result.contains(ULong.valueOf(11)));
						assertTrue(result.contains(ULong.valueOf(12)));
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// getUserSubOrgInternal()
	// =========================================================================

	@Nested
	@DisplayName("getUserSubOrgInternal()")
	class GetUserSubOrgInternalTests {

		@Test
		void getUserSubOrgInternal_OwnerRole_ReturnsAllUsers() {
			List<ULong> allUserIds = List.of(ULong.valueOf(1), ULong.valueOf(2));

			when(userService.getUserAuthorities("testApp", SYSTEM_CLIENT_ID, USER_ID))
					.thenReturn(Mono.just(List.of("Authorities.ROLE_Owner", "Authorities.Logged_IN")));
			when(dao.getUserIdsByClientId(eq(SYSTEM_CLIENT_ID), isNull()))
					.thenReturn(Flux.fromIterable(allUserIds));

			StepVerifier.create(service.getUserSubOrgInternal("testApp", SYSTEM_CLIENT_ID, USER_ID).collectList())
					.assertNext(result -> assertEquals(2, result.size()))
					.verifyComplete();
		}

		@Test
		void getUserSubOrgInternal_NonOwner_ReturnsSubOrg() {
			when(userService.getUserAuthorities("testApp", SYSTEM_CLIENT_ID, USER_ID))
					.thenReturn(Mono.just(List.of("Authorities.User_READ", "Authorities.Logged_IN")));
			when(dao.getLevel1SubOrg(SYSTEM_CLIENT_ID, USER_ID))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.getUserSubOrgInternal("testApp", SYSTEM_CLIENT_ID, USER_ID).collectList())
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals(USER_ID, result.get(0)); // self only
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// getCurrentUserSubOrgUsers()
	// =========================================================================

	@Nested
	@DisplayName("getCurrentUserSubOrgUsers()")
	class GetCurrentUserSubOrgUsersTests {

		@Test
		void getCurrentUserSubOrgUsers_OwnerRole_ReturnsAllUsers() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			List<ULong> allUserIds = List.of(USER_ID, ULong.valueOf(11));
			User user1 = TestDataFactory.createActiveUser(USER_ID, SYSTEM_CLIENT_ID);
			User user2 = TestDataFactory.createActiveUser(ULong.valueOf(11), SYSTEM_CLIENT_ID);

			when(dao.getUserIdsByClientId(eq(SYSTEM_CLIENT_ID), isNull()))
					.thenReturn(Flux.fromIterable(allUserIds));
			when(userService.readByIds(anyList(), isNull()))
					.thenReturn(Mono.just(List.of(user1, user2)));

			StepVerifier.create(service.getCurrentUserSubOrgUsers().collectList())
					.assertNext(result -> assertEquals(2, result.size()))
					.verifyComplete();
		}
	}
}
