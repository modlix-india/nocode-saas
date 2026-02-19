package com.fincity.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import com.fincity.security.dto.App;
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

		@Test
		@DisplayName("hasAccessTo - profile not found returns empty")
		void hasAccessTo_ProfileNotFound_ReturnsEmpty() {
			ULong targetClientId = ULong.valueOf(3);

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(targetClientId, BUS_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(targetClientId))
					.thenReturn(Mono.just(hierarchy));

			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.empty());

			StepVerifier.create(service.hasAccessTo(PROFILE_ID, targetClientId, null))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getUserAppHavingProfile
	// =========================================================================

	@Nested
	@DisplayName("getUserAppHavingProfile")
	class GetUserAppHavingProfileTests {

		@Test
		@DisplayName("returns appId when user has profile")
		void returnsAppId_WhenUserHasProfile() {
			ULong userId = ULong.valueOf(10);
			when(dao.getUserAppHavingProfile(userId)).thenReturn(Mono.just(APP_ID));

			StepVerifier.create(service.getUserAppHavingProfile(userId))
					.assertNext(result -> assertEquals(APP_ID, result))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty when user has no profile")
		void returnsEmpty_WhenUserHasNoProfile() {
			ULong userId = ULong.valueOf(10);
			when(dao.getUserAppHavingProfile(userId)).thenReturn(Mono.empty());

			StepVerifier.create(service.getUserAppHavingProfile(userId))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty when userId is null")
		void returnsEmpty_WhenUserIdIsNull() {
			StepVerifier.create(service.getUserAppHavingProfile(null))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getAppProfilesHavingAuthorities
	// =========================================================================

	@Nested
	@DisplayName("getAppProfilesHavingAuthorities")
	class GetAppProfilesHavingAuthoritiesTests {

		@Test
		@DisplayName("returns profile ids when authorities match")
		void returnsProfileIds_WhenAuthoritiesMatch() {
			List<String> authorities = List.of("Authorities.User_READ", "Authorities.User_UPDATE");
			List<ULong> expectedIds = List.of(PROFILE_ID, ULong.valueOf(201));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));
			when(dao.getAppProfileHavingAuthorities(APP_ID, hierarchy, authorities))
					.thenReturn(Mono.just(expectedIds));

			StepVerifier.create(service.getAppProfilesHavingAuthorities(APP_ID, SYSTEM_CLIENT_ID, authorities))
					.assertNext(result -> {
						assertEquals(2, result.size());
						assertTrue(result.contains(PROFILE_ID));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty when authorities list is null")
		void returnsEmpty_WhenAuthoritiesNull() {
			StepVerifier.create(service.getAppProfilesHavingAuthorities(APP_ID, SYSTEM_CLIENT_ID, null))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty when authorities list is empty")
		void returnsEmpty_WhenAuthoritiesEmpty() {
			StepVerifier.create(service.getAppProfilesHavingAuthorities(APP_ID, SYSTEM_CLIENT_ID, List.of()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getProfileUsers
	// =========================================================================

	@Nested
	@DisplayName("getProfileUsers")
	class GetProfileUsersTests {

		@Test
		@DisplayName("returns user ids for profiles in app")
		void returnsUserIds_ForProfilesInApp() {
			String appCode = "testApp";
			List<ULong> profileIds = List.of(PROFILE_ID);
			ULong userId1 = ULong.valueOf(10);
			ULong userId2 = ULong.valueOf(11);

			App app = TestDataFactory.createOwnApp(APP_ID, SYSTEM_CLIENT_ID, appCode);
			when(appService.getAppByCode(appCode)).thenReturn(Mono.just(app));
			when(dao.getUsersForProfiles(APP_ID, profileIds))
					.thenReturn(Flux.just(userId1, userId2));

			StepVerifier.create(service.getProfileUsers(appCode, profileIds))
					.assertNext(result -> {
						assertEquals(2, result.size());
						assertTrue(result.contains(userId1));
						assertTrue(result.contains(userId2));
					})
					.verifyComplete();
		}
	}

	// =========================================================================
	// getUsersForProfiles
	// =========================================================================

	@Nested
	@DisplayName("getUsersForProfiles")
	class GetUsersForProfilesTests {

		@Test
		@DisplayName("returns user ids from dao")
		void returnsUserIds_FromDao() {
			List<ULong> profileIds = List.of(PROFILE_ID);
			ULong userId = ULong.valueOf(10);

			when(dao.getUsersForProfiles(APP_ID, profileIds))
					.thenReturn(Flux.just(userId));

			StepVerifier.create(service.getUsersForProfiles(APP_ID, profileIds))
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals(userId, result.get(0));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty list when profileIds is null")
		void returnsEmptyList_WhenProfileIdsNull() {
			StepVerifier.create(service.getUsersForProfiles(APP_ID, null))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty list when profileIds is empty")
		void returnsEmptyList_WhenProfileIdsEmpty() {
			StepVerifier.create(service.getUsersForProfiles(APP_ID, List.of()))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// readObject
	// =========================================================================

	@Nested
	@DisplayName("readObject")
	class ReadObjectTests {

		@Test
		@DisplayName("returns profile via hierarchy")
		void returnsProfile_ViaHierarchy() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			StepVerifier.create(service.readObject(PROFILE_ID, null))
					.assertNext(result -> {
						assertEquals(PROFILE_ID, result.getId());
						assertEquals("TestProfile", result.getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty when profile not found in hierarchy")
		void returnsEmpty_WhenProfileNotFound() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.empty());

			StepVerifier.create(service.readObject(PROFILE_ID, null))
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
		@DisplayName("returns PROFILE")
		void returnsPROFILE() {
			assertEquals(com.fincity.security.jooq.enums.SecuritySoxLogObjectName.PROFILE,
					service.getSoxObjectName());
		}
	}

	// =========================================================================
	// readById (delegates to readInternal)
	// =========================================================================

	@Nested
	@DisplayName("readById")
	class ReadByIdTests {

		@Test
		@DisplayName("delegates to readInternal and returns profile")
		void delegatesToReadInternal() {
			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.readInternal(PROFILE_ID)).thenReturn(Mono.just(profile));

			StepVerifier.create(service.readById(PROFILE_ID))
					.assertNext(result -> {
						assertEquals(PROFILE_ID, result.getId());
						assertEquals("TestProfile", result.getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("returns empty when profile not found")
		void returnsEmpty_WhenNotFound() {
			when(dao.readInternal(PROFILE_ID)).thenReturn(Mono.empty());

			StepVerifier.create(service.readById(PROFILE_ID))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getProfileAuthorities - multiple profile IDs
	// =========================================================================

	@Nested
	@DisplayName("getProfileAuthorities - multiple profiles merges and deduplicates")
	class GetProfileAuthoritiesMultipleTests {

		@Test
		@DisplayName("merges authorities from multiple profiles and deduplicates")
		void mergesAuthorities_FromMultipleProfiles() {
			ULong profileId2 = ULong.valueOf(201);
			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			Set<ULong> profileIds = Set.of(PROFILE_ID, profileId2);

			when(dao.getProfileAuthorities(eq(PROFILE_ID), eq(hierarchy)))
					.thenReturn(Mono.just(List.of("Authorities.User_READ", "Authorities.User_UPDATE")));
			when(dao.getProfileAuthorities(eq(profileId2), eq(hierarchy)))
					.thenReturn(Mono.just(List.of("Authorities.User_READ", "Authorities.Role_READ")));

			StepVerifier.create(service.getProfileAuthorities(profileIds, hierarchy))
					.assertNext(result -> {
						assertTrue(result.contains("Authorities.User_READ"));
						assertTrue(result.contains("Authorities.User_UPDATE"));
						assertTrue(result.contains("Authorities.Role_READ"));
						// User_READ appears in both profiles but should be deduplicated
						long userReadCount = result.stream()
								.filter("Authorities.User_READ"::equals).count();
						assertEquals(1, userReadCount);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("handles profile with no authorities returning empty list")
		void handlesProfile_WithNoAuthorities() {
			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			Set<ULong> profileIds = Set.of(PROFILE_ID);

			when(dao.getProfileAuthorities(eq(PROFILE_ID), eq(hierarchy)))
					.thenReturn(Mono.empty());

			StepVerifier.create(service.getProfileAuthorities(profileIds, hierarchy))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getProfileAuthorities(appCode, clientId, userId) - empty profiles
	// =========================================================================

	@Nested
	@DisplayName("getProfileAuthorities by appCode - edge cases")
	class GetProfileAuthoritiesByAppCodeEdgeCaseTests {

		@Test
		@DisplayName("returns empty list when user has no profile ids")
		void returnsEmptyList_WhenNoProfileIds() {
			ULong userId = ULong.valueOf(10);
			String appCode = "testApp";

			when(dao.getProfileIds(appCode, userId)).thenReturn(Mono.just(Set.of()));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			StepVerifier.create(service.getProfileAuthorities(appCode, SYSTEM_CLIENT_ID, userId))
					.assertNext(result -> assertTrue(result.isEmpty()))
					.verifyComplete();
		}
	}

	// =========================================================================
	// delete - R2DBC integrity violation
	// =========================================================================

	@Nested
	@DisplayName("delete - additional error paths")
	class DeleteAdditionalTests {

		@Test
		@DisplayName("R2DBC data integrity violation throws forbidden")
		void delete_R2dbcIntegrityViolation_ThrowsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(dao.delete(PROFILE_ID, hierarchy)).thenReturn(
					Mono.error(new io.r2dbc.spi.R2dbcDataIntegrityViolationException("FK constraint")));

			StepVerifier.create(service.delete(PROFILE_ID))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		@DisplayName("non-integrity error propagates as-is")
		void delete_OtherError_Propagates() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			RuntimeException runtimeEx = new RuntimeException("unexpected error");
			when(dao.delete(PROFILE_ID, hierarchy)).thenReturn(Mono.error(runtimeEx));

			StepVerifier.create(service.delete(PROFILE_ID))
					.expectErrorMatches(e -> e instanceof RuntimeException
							&& "unexpected error".equals(e.getMessage()))
					.verify();
		}
	}

	// =========================================================================
	// create - updatableEntity throws NotImplementedException
	// =========================================================================

	@Nested
	@DisplayName("updatableEntity")
	class UpdatableEntityTests {

		@Test
		@DisplayName("throws NotImplementedException")
		void updatableEntity_ThrowsNotImplemented() {
			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");

			// updatableEntity is protected but we can test via update which calls it
			// The method directly throws NotImplementedException
			// Access via reflection to test directly
			try {
				java.lang.reflect.Method method = ProfileService.class.getDeclaredMethod("updatableEntity",
						com.fincity.saas.commons.model.dto.AbstractUpdatableDTO.class);
				method.setAccessible(true);
				@SuppressWarnings("unchecked")
				Mono<Profile> result = (Mono<Profile>) method.invoke(service, profile);

				StepVerifier.create(result)
						.expectError(org.apache.commons.lang.NotImplementedException.class)
						.verify();
			} catch (NoSuchMethodException e) {
				// Try the overloaded method with Profile parameter type
				try {
					java.lang.reflect.Method method = ProfileService.class.getDeclaredMethod("updatableEntity",
							Profile.class);
					method.setAccessible(true);
					@SuppressWarnings("unchecked")
					Mono<Profile> result = (Mono<Profile>) method.invoke(service, profile);

					StepVerifier.create(result)
							.expectError(org.apache.commons.lang.NotImplementedException.class)
							.verify();
				} catch (Exception ex) {
					throw new RuntimeException("Failed to invoke updatableEntity", ex);
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to invoke updatableEntity", e);
			}
		}
	}

	// =========================================================================
	// assignedProfiles - filtering via read
	// =========================================================================

	@Nested
	@DisplayName("assignedProfiles - additional edge cases")
	class AssignedProfilesAdditionalTests {

		@Test
		@DisplayName("skips profiles that user has no read access to")
		void skipsProfiles_WhenNoReadAccess() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ULong userId = ULong.valueOf(10);
			ULong profileId2 = ULong.valueOf(201);

			when(dao.getAssignedProfileIds(userId, APP_ID))
					.thenReturn(Flux.just(PROFILE_ID, profileId2));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile1 = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"Profile1");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile1));

			// Second profile not accessible - read returns empty
			when(dao.read(profileId2, hierarchy)).thenReturn(Mono.empty());

			when(appService.hasReadAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.assignedProfiles(userId, APP_ID))
					.assertNext(result -> {
						assertEquals(1, result.size());
						assertEquals("Profile1", result.get(0).getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("deduplicates profile ids")
		void deduplicatesProfileIds() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ULong userId = ULong.valueOf(10);

			// Same profile ID returned twice
			when(dao.getAssignedProfileIds(userId, APP_ID))
					.thenReturn(Flux.just(PROFILE_ID, PROFILE_ID));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Profile profile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"TestProfile");
			when(dao.read(PROFILE_ID, hierarchy)).thenReturn(Mono.just(profile));

			when(appService.hasReadAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.assignedProfiles(userId, APP_ID))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// create - business client creating profile for own client
	// =========================================================================

	@Nested
	@DisplayName("create - additional validation paths")
	class CreateAdditionalTests {

		@Test
		@DisplayName("business client creating own profile without specifying clientId")
		void businessClient_OwnProfile_NullClientId_SetsFromContext() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Profile_CREATE", "Authorities.Profile_UPDATE",
							"Authorities.Logged_IN"));
			setupSecurityContext(ca);

			Profile profile = TestDataFactory.createProfile(null, null, APP_ID, "MyProfile");
			profile.setArrangement(Map.of());

			when(appService.hasReadAccess(eq(APP_ID), isNull()))
					.thenReturn(Mono.just(true));
			when(appService.hasReadAccess(eq(APP_ID), eq(BUS_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			ClientHierarchy hierarchy = TestDataFactory.createLevel0Hierarchy(BUS_CLIENT_ID,
					SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(BUS_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.hasAccessToRoles(eq(APP_ID), eq(hierarchy), any(Profile.class)))
					.thenReturn(Mono.just(true));

			Profile createdProfile = TestDataFactory.createProfile(PROFILE_ID, BUS_CLIENT_ID, APP_ID,
					"MyProfile");
			createdProfile.setArrangement(Map.of());

			when(dao.createUpdateProfile(any(Profile.class), any(ULong.class), eq(hierarchy)))
					.thenReturn(Mono.just(createdProfile));

			StepVerifier.create(service.create(profile))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(BUS_CLIENT_ID, result.getClientId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("create with null arrangement succeeds")
		void create_NullArrangement_Succeeds() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			Profile profile = TestDataFactory.createProfile(null, null, APP_ID, "NoArrangement");
			// Explicitly no arrangement set (null)

			when(appService.hasReadAccess(eq(APP_ID), isNull()))
					.thenReturn(Mono.just(true));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(dao.hasAccessToRoles(eq(APP_ID), eq(hierarchy), any(Profile.class)))
					.thenReturn(Mono.just(true));

			Profile createdProfile = TestDataFactory.createProfile(PROFILE_ID, SYSTEM_CLIENT_ID, APP_ID,
					"NoArrangement");

			when(dao.createUpdateProfile(any(Profile.class), any(ULong.class), eq(hierarchy)))
					.thenReturn(Mono.just(createdProfile));

			StepVerifier.create(service.create(profile))
					.assertNext(result -> assertNotNull(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// hasAccessToProfiles - empty profile IDs
	// =========================================================================

	@Nested
	@DisplayName("hasAccessToProfiles - additional edge cases")
	class HasAccessToProfilesAdditionalTests {

		@Test
		@DisplayName("empty profile IDs set still calls dao")
		void emptyProfileIds_StillCallsDao() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Set<ULong> emptySet = Set.of();
			when(dao.hasAccessToProfiles(BUS_CLIENT_ID, emptySet))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.hasAccessToProfiles(BUS_CLIENT_ID, emptySet))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("multiple profile IDs - delegates correctly")
		void multipleProfileIds_DelegatesCorrectly() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Set<ULong> profileIds = Set.of(PROFILE_ID, ULong.valueOf(201), ULong.valueOf(202));
			when(dao.hasAccessToProfiles(BUS_CLIENT_ID, profileIds))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.hasAccessToProfiles(BUS_CLIENT_ID, profileIds))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// hasAccessToRoles - empty role IDs
	// =========================================================================

	@Nested
	@DisplayName("hasAccessToRoles - additional edge cases")
	class HasAccessToRolesAdditionalTests {

		@Test
		@DisplayName("empty role IDs set - delegates to dao")
		void emptyRoleIds_DelegatesToDao() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Set<ULong> emptySet = Set.of();
			when(dao.hasAccessToRoles(BUS_CLIENT_ID, hierarchy, emptySet))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.hasAccessToRoles(BUS_CLIENT_ID, emptySet))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		@DisplayName("multiple role IDs checked together")
		void multipleRoleIds_CheckedTogether() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			Set<ULong> roleIds = Set.of(ROLE_ID, ULong.valueOf(301), ULong.valueOf(302));
			when(dao.hasAccessToRoles(BUS_CLIENT_ID, hierarchy, roleIds))
					.thenReturn(Mono.just(false));

			StepVerifier.create(service.hasAccessToRoles(BUS_CLIENT_ID, roleIds))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// restrictClient - additional edge cases
	// =========================================================================

	@Nested
	@DisplayName("restrictClient - additional scenarios")
	class RestrictClientAdditionalTests {

		@Test
		@DisplayName("business client restricting managed client profile")
		void businessClient_RestrictsProfile() {
			ContextAuthentication ca = TestDataFactory.createBusinessAuth(BUS_CLIENT_ID, "BUSCLIENT",
					List.of("Authorities.Profile_READ", "Authorities.Client_UPDATE",
							"Authorities.Logged_IN"));
			setupSecurityContext(ca);

			ULong targetClientId = ULong.valueOf(5);
			when(clientService.isUserClientManageClient(eq(ca), eq(targetClientId)))
					.thenReturn(Mono.just(true));
			when(dao.checkProfileAppAccess(PROFILE_ID, targetClientId))
					.thenReturn(Mono.just(true));
			when(dao.restrictClient(PROFILE_ID, targetClientId))
					.thenReturn(Mono.just(true));

			StepVerifier.create(service.restrictClient(PROFILE_ID, targetClientId))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}
	}

	// =========================================================================
	// getRolesForAssignmentInApp - app not found
	// =========================================================================

	@Nested
	@DisplayName("getRolesForAssignmentInApp - additional scenarios")
	class GetRolesForAssignmentInAppAdditionalTests {

		@Test
		@DisplayName("returns multiple roles")
		void returnsMultipleRoles() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			String appCode = "testApp";
			when(appService.getAppId(appCode)).thenReturn(Mono.just(APP_ID));

			ClientHierarchy hierarchy = TestDataFactory.createSystemHierarchy(SYSTEM_CLIENT_ID);
			when(clientHierarchyService.getClientHierarchy(SYSTEM_CLIENT_ID))
					.thenReturn(Mono.just(hierarchy));

			when(appService.hasReadAccess(eq(APP_ID), eq(SYSTEM_CLIENT_ID)))
					.thenReturn(Mono.just(true));

			RoleV2 role1 = TestDataFactory.createRoleV2(ROLE_ID, SYSTEM_CLIENT_ID, APP_ID, "AdminRole");
			RoleV2 role2 = TestDataFactory.createRoleV2(ULong.valueOf(301), SYSTEM_CLIENT_ID, APP_ID,
					"UserRole");
			when(dao.getRolesForAssignmentInApp(APP_ID, hierarchy))
					.thenReturn(Mono.just(List.of(role1, role2)));

			StepVerifier.create(service.getRolesForAssignmentInApp(appCode))
					.assertNext(result -> {
						assertEquals(2, result.size());
						assertEquals("AdminRole", result.get(0).getName());
						assertEquals("UserRole", result.get(1).getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("app not found returns forbidden")
		void appNotFound_ReturnsForbidden() {
			ContextAuthentication ca = TestDataFactory.createSystemAuth();
			setupSecurityContext(ca);

			String appCode = "nonexistent";
			when(appService.getAppId(appCode)).thenReturn(Mono.empty());

			StepVerifier.create(service.getRolesForAssignmentInApp(appCode))
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == HttpStatus.FORBIDDEN)
					.verify();
		}
	}
}
