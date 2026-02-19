package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.ProfileDAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest extends AbstractServiceUnitTest {

	@Mock
	private ProfileDAO dao;

	@Mock
	private SecurityMessageResourceService messageResourceService;

	@Mock
	private ClientService clientService;

	@Mock
	private ClientHierarchyService clientHierarchyService;

	@Mock
	private AppService appService;

	@Mock
	private CacheService cacheService;

	@Mock
	private RoleV2Service roleService;

	@Mock
	private SoxLogService soxLogService;

	private ProfileService service;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private static final ULong BUS_CLIENT_ID = ULong.valueOf(2);
	private static final ULong APP_ID = ULong.valueOf(100);
	private static final ULong PROFILE_ID = ULong.valueOf(200);
	private static final ULong ROLE_ID = ULong.valueOf(300);

	@BeforeEach
	void setUp() {
		service = new ProfileService(messageResourceService, clientService,
				clientHierarchyService, cacheService, appService, roleService);

		// ProfileService -> AbstractSecurityUpdatableDataService ->
		// AbstractJOOQUpdatableDataService -> AbstractJOOQDataService (has dao)
		// 3 getSuperclass() calls
		try {
			var daoField = service.getClass().getSuperclass().getSuperclass().getSuperclass()
					.getDeclaredField("dao");
			daoField.setAccessible(true);
			daoField.set(service, dao);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject DAO", e);
		}

		// SoxLogService is @Autowired private in AbstractSecurityUpdatableDataService
		// 1 getSuperclass() call from ProfileService
		try {
			var soxLogField = service.getClass().getSuperclass().getDeclaredField("soxLogService");
			soxLogField.setAccessible(true);
			soxLogField.set(service, soxLogService);
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject SoxLogService", e);
		}

		setupMessageResourceService(messageResourceService);
		setupCacheService(cacheService);
		setupSoxLogService(soxLogService);
	}

	@Nested
	class CreateTests {

		@Test
		void create_WithAppAccess_SystemClient_CreatesProfile() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Profile profile = TestDataFactory.createProfile(null, null, APP_ID, "TestProfile");
			profile.setArrangement(Map.of());

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);

			when(appService.hasReadAccess(eq(APP_ID), isNull()))
					.thenReturn(Mono.just(true));

			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.hasAccessToRoles(eq(APP_ID), eq(hierarchy), eq(profile)))
					.thenReturn(Mono.just(true));

			Profile createdProfile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			createdProfile.setArrangement(Map.of());

			when(dao.createUpdateProfile(any(Profile.class), any(ULong.class), eq(hierarchy)))
					.thenReturn(Mono.just(createdProfile));

			StepVerifier.create(service.create(profile))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(PROFILE_ID, result.getId());
						assertEquals("TestProfile", result.getName());
					})
					.verifyComplete();
		}

		@Test
		void create_NullAppId_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Profile profile = TestDataFactory.createProfile(null, SYSTEM_CLIENT_ID, null, "TestProfile");

			StepVerifier.create(service.create(profile))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void create_NoAppAccess_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Profile profile = TestDataFactory.createProfile(null, BUS_CLIENT_ID, APP_ID, "TestProfile");
			profile.setArrangement(Map.of());

			when(appService.hasReadAccess(eq(APP_ID), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(profile))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void create_NonSystemClient_WithClientIdSet_ChecksManageAccess() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Profile_CREATE", "Authorities.Profile_UPDATE",
							"Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ULong targetClientId = ULong.valueOf(3);
			Profile profile = TestDataFactory.createProfile(null, targetClientId, APP_ID, "TestProfile");
			profile.setArrangement(Map.of());

			when(appService.hasReadAccess(eq(APP_ID), eq(targetClientId)))
					.thenReturn(Mono.just(true));
			when(appService.hasReadAccess(eq(APP_ID), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(clientService.isUserClientManageClient(eq(ca), eq(targetClientId)))
					.thenReturn(Mono.just(true));

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(targetClientId, BUS_CLIENT_ID);

			when(clientHierarchyService.getClientHierarchy(targetClientId))
					.thenReturn(Mono.just(hierarchy));

			when(dao.hasAccessToRoles(eq(APP_ID), eq(hierarchy), eq(profile)))
					.thenReturn(Mono.just(true));

			Profile createdProfile = TestDataFactory.createProfile(PROFILE_ID, targetClientId, APP_ID,
					"TestProfile");
			createdProfile.setArrangement(Map.of());

			when(dao.createUpdateProfile(any(Profile.class), any(ULong.class), eq(hierarchy)))
					.thenReturn(Mono.just(createdProfile));

			StepVerifier.create(service.create(profile))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();
		}

		@Test
		void create_NonSystemClient_NotManaged_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Profile_CREATE", "Authorities.Profile_UPDATE",
							"Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ULong targetClientId = ULong.valueOf(3);
			Profile profile = TestDataFactory.createProfile(null, targetClientId, APP_ID, "TestProfile");
			profile.setArrangement(Map.of());

			when(appService.hasReadAccess(eq(APP_ID), eq(targetClientId)))
					.thenReturn(Mono.just(true));
			when(appService.hasReadAccess(eq(APP_ID), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(clientService.isUserClientManageClient(eq(ca), eq(targetClientId)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(profile))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void create_SystemClient_NullClientId_SetsClientIdFromContext() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Profile profile = TestDataFactory.createProfile(null, null, APP_ID, "TestProfile");
			profile.setArrangement(Map.of());

			when(appService.hasReadAccess(eq(APP_ID), isNull()))
					.thenReturn(Mono.just(true));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);

			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.hasAccessToRoles(eq(APP_ID), eq(hierarchy), any(Profile.class)))
					.thenReturn(Mono.just(true));

			Profile createdProfile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			createdProfile.setArrangement(Map.of());

			when(dao.createUpdateProfile(any(Profile.class), any(ULong.class), eq(hierarchy)))
					.thenReturn(Mono.just(createdProfile));

			StepVerifier.create(service.create(profile))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();
		}

		@Test
		void create_NoAccessToRoles_ThrowsBadRequest() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Profile profile = TestDataFactory.createProfile(null, null, APP_ID, "TestProfile");
			profile.setArrangement(Map.of());

			when(appService.hasReadAccess(eq(APP_ID), isNull()))
					.thenReturn(Mono.just(true));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);

			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.hasAccessToRoles(eq(APP_ID), eq(hierarchy), any(Profile.class)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.create(profile))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.BAD_REQUEST)
					.verify();
		}

		@Test
		void create_NonSystemClient_ClientAlsoHasNoAppAccess_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Profile_CREATE", "Authorities.Profile_UPDATE",
							"Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Profile profile = TestDataFactory.createProfile(null, BUS_CLIENT_ID, APP_ID, "TestProfile");
			profile.setArrangement(Map.of());

			when(appService.hasReadAccess(eq(APP_ID), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true)).thenReturn(Mono.just(false));

			StepVerifier.create(service.create(profile))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	@Nested
	class ReadTests {

		@Test
		void read_ReturnsProfile() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(appService.hasReadAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.read(PROFILE_ID))
					.assertNext(result -> {
						assertEquals(PROFILE_ID, result.getId());
						assertEquals("TestProfile", result.getName());
					})
					.verifyComplete();
		}

		@Test
		void read_NoAppAccess_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(appService.hasReadAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.read(PROFILE_ID))
					.verifyComplete();
		}

		@Test
		void read_ProfileNotFound_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.empty());

			StepVerifier.create(service.read(PROFILE_ID))
					.verifyComplete();
		}
	}

	@Nested
	class ReadAllTests {

		@Test
		void readAll_ReturnsPage() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Pageable pageable = PageRequest.of(0, 10);
			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			Page<Profile> page = new PageImpl<>(List.of(profile), pageable, 1);

			when(dao.readAll(APP_ID, hierarchy, pageable)).thenReturn(Mono.just(page));

			StepVerifier.create(service.readAll(APP_ID, pageable))
					.assertNext(result -> {
						assertEquals(1, result.getTotalElements());
						assertEquals("TestProfile", result.getContent().get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		void readAll_EmptyResult_ReturnsEmptyPage() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Pageable pageable = PageRequest.of(0, 10);
			Page<Profile> emptyPage = new PageImpl<>(List.of(), pageable, 0);

			when(dao.readAll(APP_ID, hierarchy, pageable)).thenReturn(Mono.just(emptyPage));

			StepVerifier.create(service.readAll(APP_ID, pageable))
					.assertNext(result -> {
						assertEquals(0, result.getTotalElements());
						assertTrue(result.getContent().isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	class DeleteTests {

		@Test
		void delete_HappyPath_DeletesProfile() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(dao.delete(PROFILE_ID, hierarchy)).thenReturn(Mono.just(1));

			StepVerifier.create(service.delete(PROFILE_ID))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void delete_DataIntegrityViolation_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(dao.delete(PROFILE_ID, hierarchy)).thenReturn(
					Mono.error(new org.jooq.exception.DataAccessException("integrity violation")));

			StepVerifier.create(service.delete(PROFILE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		void delete_ProfileNotFound_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.empty());

			StepVerifier.create(service.delete(PROFILE_ID))
					.verifyComplete();
		}
	}

	@Nested
	class RestrictClientTests {

		@Test
		void restrictClient_HasAccess_RestrictsClient() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(eq(ca), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(dao.checkProfileAppAccess(PROFILE_ID, BUS_CLIENT_ID))
					.thenReturn(Mono.just(true));

			when(dao.restrictClient(PROFILE_ID, BUS_CLIENT_ID))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.restrictClient(PROFILE_ID, BUS_CLIENT_ID))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void restrictClient_NotManaged_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(eq(ca), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.restrictClient(PROFILE_ID, BUS_CLIENT_ID))
					.verifyComplete();
		}

		@Test
		void restrictClient_NoAppAccess_ReturnsEmpty() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			when(clientService.isUserClientManageClient(eq(ca), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			when(dao.checkProfileAppAccess(PROFILE_ID, BUS_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.restrictClient(PROFILE_ID, BUS_CLIENT_ID))
					.verifyComplete();
		}
	}

	@Nested
	class HasAccessToRolesTests {

		@Test
		void hasAccessToRoles_ReturnsTrue() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Set<ULong> roleIds = Set.of(ROLE_ID);
			when(dao.hasAccessToRoles(BUS_CLIENT_ID, hierarchy, roleIds))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.hasAccessToRoles(BUS_CLIENT_ID, roleIds))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void hasAccessToRoles_ReturnsFalse() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Set<ULong> roleIds = Set.of(ROLE_ID);
			when(dao.hasAccessToRoles(BUS_CLIENT_ID, hierarchy, roleIds))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.hasAccessToRoles(BUS_CLIENT_ID, roleIds))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	class HasAccessToProfilesTests {

		@Test
		void hasAccessToProfiles_ReturnsTrue() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Set<ULong> profileIds = Set.of(PROFILE_ID);
			when(dao.hasAccessToProfiles(BUS_CLIENT_ID, profileIds))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.hasAccessToProfiles(BUS_CLIENT_ID, profileIds))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void hasAccessToProfiles_ReturnsFalse() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Set<ULong> profileIds = Set.of(PROFILE_ID);
			when(dao.hasAccessToProfiles(BUS_CLIENT_ID, profileIds))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.hasAccessToProfiles(BUS_CLIENT_ID, profileIds))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	class GetProfileAuthoritiesTests {

		@Test
		void getProfileAuthorities_WithClientHierarchy_ReturnsAuthorities() {
			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			Set<ULong> profileIds = Set.of(PROFILE_ID);

			when(dao.getProfileAuthorities(eq(PROFILE_ID), eq(hierarchy)))
					.thenReturn(Mono.just(List.of("Authorities.ROLE_Admin", "Authorities.User_READ")));

			StepVerifier.create(service.getProfileAuthorities(profileIds, hierarchy))
					.assertNext(result -> {
						assertNotNull(result);
						assertTrue(result.contains("Authorities.ROLE_Admin"));
						assertTrue(result.contains("Authorities.User_READ"));
					})
					.verifyComplete();
		}

		@Test
		void getProfileAuthorities_EmptyProfileIds_ReturnsEmptyList() {
			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			Set<ULong> profileIds = Set.of();

			StepVerifier.create(service.getProfileAuthorities(profileIds, hierarchy))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}

		@Test
		void getProfileAuthorities_ByAppCodeAndUserId_ReturnsAuthorities() {
			ULong userId = ULong.valueOf(10);
			String appCode = "testApp";

			Set<ULong> profileIds = Set.of(PROFILE_ID);
			when(dao.getProfileIds(appCode, userId)).thenReturn(Mono.just(profileIds));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.getProfileAuthorities(eq(PROFILE_ID), eq(hierarchy)))
					.thenReturn(Mono.just(List.of("Authorities.ROLE_Admin")));

			StepVerifier.create(service.getProfileAuthorities(appCode, SYSTEM_CLIENT_ID, userId))
					.assertNext(result -> {
						assertNotNull(result);
						assertTrue(result.contains("Authorities.ROLE_Admin"));
					})
					.verifyComplete();
		}
	}

	@Nested
	class CheckIfUserHasAnyProfileTests {

		@Test
		void checkIfUserHasAnyProfile_HasProfile_ReturnsTrue() {
			ULong userId = ULong.valueOf(10);
			String appCode = "testApp";

			when(dao.checkIfUserHasAnyProfile(userId, appCode))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.checkIfUserHasAnyProfile(userId, appCode))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void checkIfUserHasAnyProfile_NoProfile_ReturnsFalse() {
			ULong userId = ULong.valueOf(10);
			String appCode = "testApp";

			when(dao.checkIfUserHasAnyProfile(userId, appCode))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.checkIfUserHasAnyProfile(userId, appCode))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void checkIfUserHasAnyProfile_NullUserId_ReturnsFalse() {
			StepVerifier.create(service.checkIfUserHasAnyProfile(null, "testApp"))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void checkIfUserHasAnyProfile_BlankAppCode_ReturnsFalse() {
			ULong userId = ULong.valueOf(10);

			StepVerifier.create(service.checkIfUserHasAnyProfile(userId, ""))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void checkIfUserHasAnyProfile_NullAppCode_ReturnsFalse() {
			ULong userId = ULong.valueOf(10);

			StepVerifier.create(service.checkIfUserHasAnyProfile(userId, null))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	class GetRolesForAssignmentInAppTests {

		@Test
		void getRolesForAssignmentInApp_HasAccess_ReturnsRoles() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			String appCode = "testApp";

			when(appService.getAppId(appCode)).thenReturn(Mono.just(APP_ID));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(appService.hasReadAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			RoleV2 role = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID, "AdminRole");
			when(dao.getRolesForAssignmentInApp(APP_ID, hierarchy))
					.thenReturn(Mono.just(List.of(role)));

			StepVerifier.create(service.getRolesForAssignmentInApp(appCode))
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals("AdminRole", result.get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		void getRolesForAssignmentInApp_NoAppAccess_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			String appCode = "testApp";

			when(appService.getAppId(appCode)).thenReturn(Mono.just(APP_ID));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(appService.hasReadAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.getRolesForAssignmentInApp(appCode))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}

	@Nested
	class AssignedProfilesTests {

		@Test
		void assignedProfiles_ReturnsProfiles() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ULong userId = ULong.valueOf(10);

			when(dao.getAssignedProfileIds(userId, APP_ID))
					.thenReturn(Flux.just(PROFILE_ID));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(appService.hasReadAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.assignedProfiles(userId, APP_ID))
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals("TestProfile", result.get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		void assignedProfiles_NoProfiles_ReturnsEmptyList() {
			ULong userId = ULong.valueOf(10);

			when(dao.getAssignedProfileIds(userId, APP_ID))
					.thenReturn(Flux.empty());

			StepVerifier.create(service.assignedProfiles(userId, APP_ID))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	class ReadInternalTests {

		@Test
		void readInternal_ReturnsProfile() {
			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.readInternal(PROFILE_ID)).thenReturn(Mono.just(profile));

			StepVerifier.create(service.readInternal(PROFILE_ID))
					.assertNext(result -> {
						assertEquals(PROFILE_ID, result.getId());
						assertEquals("TestProfile", result.getName());
					})
					.verifyComplete();
		}

		@Test
		void readInternal_NotFound_ReturnsEmpty() {
			when(dao.readInternal(PROFILE_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.readInternal(PROFILE_ID))
					.verifyComplete();
		}
	}

	@Nested
	class HasAccessToTests {

		@Test
		void hasAccessTo_ClientManagesProfile_ReturnsTrue() {
			ULong targetClientId = ULong.valueOf(3);

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(targetClientId, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(targetClientId))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, BUS_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(clientService.doesClientManageClient(BUS_CLIENT_ID, targetClientId))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.hasAccessTo(PROFILE_ID, targetClientId, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void hasAccessTo_ProfileClientManagesTarget_ReturnsTrue() {
			ULong targetClientId = ULong.valueOf(3);

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(targetClientId, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(targetClientId))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, BUS_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(clientService.doesClientManageClient(BUS_CLIENT_ID, targetClientId))
					.thenReturn(Mono.just(false));
			when(clientService.doesClientManageClient(targetClientId, BUS_CLIENT_ID))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.hasAccessTo(PROFILE_ID, targetClientId, null))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void hasAccessTo_NoAccess_ReturnsFalse() {
			ULong targetClientId = ULong.valueOf(3);

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(targetClientId, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(targetClientId))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, BUS_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(clientService.doesClientManageClient(BUS_CLIENT_ID, targetClientId))
					.thenReturn(Mono.just(false));
			when(clientService.doesClientManageClient(targetClientId, BUS_CLIENT_ID))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.hasAccessTo(PROFILE_ID, targetClientId, null))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}
}
