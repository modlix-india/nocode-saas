package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dao.ProfileDAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.Profile;
import com.fincity.security.integration.AbstractIntegrationTest;
import com.fincity.security.testutil.TestDataFactory;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProfileDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ProfileDAO profileDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);
	private final ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				// Clean profile client restrictions (test-created profiles)
				.then(databaseClient.sql("DELETE FROM security_profile_client_restriction WHERE PROFILE_ID IN (SELECT ID FROM security_profile WHERE NAME NOT IN ('Appbuilder Owner'))").then())
				// Clean profile-role for test-created profiles (preserve seed Appbuilder Owner roles)
				.then(databaseClient.sql("DELETE FROM security_profile_role WHERE PROFILE_ID IN (SELECT ID FROM security_profile WHERE NAME NOT IN ('Appbuilder Owner'))").then())
				// Clean profile-user for test users
				.then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
				// Clean test role permissions and role-role relationships
				.then(databaseClient.sql("DELETE FROM security_v2_role_permission WHERE ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1)").then())
				.then(databaseClient.sql("DELETE FROM security_v2_role_role WHERE ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1) OR SUB_ROLE_ID IN (SELECT ID FROM security_v2_role WHERE CLIENT_ID > 1)").then())
				.then(databaseClient.sql("DELETE FROM security_v2_role WHERE CLIENT_ID > 1").then())
				// Clean test-created profiles (including system-client ones, but preserve seed data)
				.then(databaseClient.sql("DELETE FROM security_profile WHERE NAME NOT IN ('Appbuilder Owner')").then())
				// Clean test-created permissions
				.then(databaseClient.sql("DELETE FROM security_permission WHERE NAME LIKE 'Custom%'").then())
				.then(databaseClient.sql("DELETE FROM security_app_access WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql(
						"DELETE FROM security_app WHERE APP_CODE NOT IN ('appbuilder', 'nothing')").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> getAppbuilderAppId() {
		return databaseClient.sql("SELECT ID FROM security_app WHERE APP_CODE = 'appbuilder' LIMIT 1")
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	private Mono<ULong> getAppbuilderOwnerProfileId() {
		return databaseClient
				.sql("SELECT ID FROM security_profile WHERE NAME = 'Appbuilder Owner' LIMIT 1")
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	/**
	 * Creates a ClientHierarchy for the system client (no managing levels).
	 */
	private ClientHierarchy systemHierarchy() {
		ClientHierarchy h = new ClientHierarchy();
		h.setClientId(SYSTEM_CLIENT_ID);
		return h;
	}

	/**
	 * Creates a ClientHierarchy for a child client managed by the system client.
	 */
	private ClientHierarchy childHierarchy(ULong childClientId) {
		ClientHierarchy h = new ClientHierarchy();
		h.setClientId(childClientId);
		h.setManageClientLevel0(SYSTEM_CLIENT_ID);
		return h;
	}

	/**
	 * Inserts a profile directly via raw SQL (bypasses DAO restrictions).
	 */
	private Mono<ULong> insertTestProfile(ULong clientId, ULong appId, String name, String description,
			ULong rootProfileId, boolean defaultProfile, String arrangementJson) {
		String sql = "INSERT INTO security_profile (CLIENT_ID, APP_ID, NAME, DESCRIPTION, ROOT_PROFILE_ID, DEFAULT_PROFILE, ARRANGEMENT) "
				+ "VALUES (:clientId, :appId, :name, :description, :rootProfileId, :defaultProfile, :arrangement)";

		var spec = databaseClient.sql(sql)
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.bind("name", name);

		spec = description != null ? spec.bind("description", description)
				: spec.bindNull("description", String.class);
		spec = rootProfileId != null ? spec.bind("rootProfileId", rootProfileId.longValue())
				: spec.bindNull("rootProfileId", Long.class);
		spec = spec.bind("defaultProfile", defaultProfile ? 1 : 0);
		spec = arrangementJson != null ? spec.bind("arrangement", arrangementJson)
				: spec.bindNull("arrangement", String.class);

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	/**
	 * Inserts a v2 role directly via raw SQL.
	 */
	private Mono<ULong> insertTestRole(ULong clientId, ULong appId, String name) {
		String sql = appId != null
				? "INSERT INTO security_v2_role (CLIENT_ID, APP_ID, NAME) VALUES (:clientId, :appId, :name)"
				: "INSERT INTO security_v2_role (CLIENT_ID, NAME) VALUES (:clientId, :name)";

		var spec = databaseClient.sql(sql)
				.bind("clientId", clientId.longValue())
				.bind("name", name);

		if (appId != null) {
			spec = spec.bind("appId", appId.longValue());
		}

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	/**
	 * Inserts a profile-role association.
	 */
	private Mono<Void> insertProfileRole(ULong profileId, ULong roleId, int exclude) {
		return databaseClient.sql(
				"INSERT INTO security_profile_role (PROFILE_ID, ROLE_ID, EXCLUDE) VALUES (:profileId, :roleId, :exclude)")
				.bind("profileId", profileId.longValue())
				.bind("roleId", roleId.longValue())
				.bind("exclude", exclude)
				.then();
	}

	/**
	 * Inserts a permission directly via raw SQL.
	 */
	private Mono<ULong> insertTestPermission(ULong clientId, ULong appId, String name) {
		String sql = appId != null
				? "INSERT INTO security_permission (CLIENT_ID, APP_ID, NAME) VALUES (:clientId, :appId, :name)"
				: "INSERT INTO security_permission (CLIENT_ID, NAME) VALUES (:clientId, :name)";

		var spec = databaseClient.sql(sql)
				.bind("clientId", clientId.longValue())
				.bind("name", name);

		if (appId != null) {
			spec = spec.bind("appId", appId.longValue());
		}

		return spec.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	/**
	 * Inserts a role-permission association.
	 */
	private Mono<Void> insertRolePermission(ULong roleId, ULong permissionId) {
		return databaseClient.sql(
				"INSERT INTO security_v2_role_permission (ROLE_ID, PERMISSION_ID) VALUES (:roleId, :permissionId)")
				.bind("roleId", roleId.longValue())
				.bind("permissionId", permissionId.longValue())
				.then();
	}

	/**
	 * Inserts a role-role (sub-role) association.
	 */
	private Mono<Void> insertRoleRole(ULong roleId, ULong subRoleId) {
		return databaseClient.sql(
				"INSERT INTO security_v2_role_role (ROLE_ID, SUB_ROLE_ID) VALUES (:roleId, :subRoleId)")
				.bind("roleId", roleId.longValue())
				.bind("subRoleId", subRoleId.longValue())
				.then();
	}

	/**
	 * Inserts an app access entry.
	 */
	private Mono<Void> insertAppAccess(ULong clientId, ULong appId) {
		return databaseClient.sql(
				"INSERT INTO security_app_access (CLIENT_ID, APP_ID) VALUES (:clientId, :appId)")
				.bind("clientId", clientId.longValue())
				.bind("appId", appId.longValue())
				.then();
	}

	/**
	 * Inserts a profile-user association.
	 */
	private Mono<Void> insertProfileUser(ULong profileId, ULong userId) {
		return databaseClient.sql(
				"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
				.bind("profileId", profileId.longValue())
				.bind("userId", userId.longValue())
				.then();
	}

	// ========================================================================
	// Existing tests
	// ========================================================================

	@Nested
	@DisplayName("getProfileIds()")
	class GetProfileIdsTests {

		@Test
		@DisplayName("user with assigned profile should return profile IDs")
		void userWithProfile_ReturnsIds() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<Set<ULong>> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> insertTestUser(SYSTEM_CLIENT_ID, "profids_" + ts,
							"profids_" + ts + "@test.com", "password123")
							.flatMap(userId -> insertProfileUser(profileId, userId)
									.then(profileDAO.getProfileIds("appbuilder", userId))));

			StepVerifier.create(result)
					.assertNext(ids -> {
						assertNotNull(ids);
						assertFalse(ids.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("user without profile falls back to default profiles")
		void userWithoutProfile_FallsBackToDefault() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<Set<ULong>> result = insertTestUser(SYSTEM_CLIENT_ID, "noprof_" + ts,
					"noprof_" + ts + "@test.com", "password123")
					.flatMap(userId -> profileDAO.getProfileIds("appbuilder", userId));

			StepVerifier.create(result)
					.assertNext(ids -> assertNotNull(ids))
					.verifyComplete();
		}

		@Test
		@DisplayName("null appCode returns all profile IDs for user")
		void nullAppCode_ReturnsAllProfiles() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<Set<ULong>> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> insertTestUser(SYSTEM_CLIENT_ID, "nullac_" + ts,
							"nullac_" + ts + "@test.com", "password123")
							.flatMap(userId -> insertProfileUser(profileId, userId)
									.then(profileDAO.getProfileIds(null, userId))));

			StepVerifier.create(result)
					.assertNext(ids -> {
						assertNotNull(ids);
						assertFalse(ids.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("nothing appCode returns all profile IDs for user")
		void nothingAppCode_ReturnsAllProfiles() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<Set<ULong>> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> insertTestUser(SYSTEM_CLIENT_ID, "nothac_" + ts,
							"nothac_" + ts + "@test.com", "password123")
							.flatMap(userId -> insertProfileUser(profileId, userId)
									.then(profileDAO.getProfileIds("nothing", userId))));

			StepVerifier.create(result)
					.assertNext(ids -> {
						assertNotNull(ids);
						assertFalse(ids.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkIfUserHasAnyProfile()")
	class CheckIfUserHasAnyProfileTests {

		@Test
		@DisplayName("user with assigned profile returns true")
		void userWithProfile_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<Boolean> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> insertTestUser(SYSTEM_CLIENT_ID, "hasprof_" + ts,
							"hasprof_" + ts + "@test.com", "password123")
							.flatMap(userId -> insertProfileUser(profileId, userId)
									.then(profileDAO.checkIfUserHasAnyProfile(userId, "appbuilder"))));

			StepVerifier.create(result)
					.assertNext(hasProfile -> assertTrue(hasProfile))
					.verifyComplete();
		}

		@Test
		@DisplayName("user without profile checks for default profiles")
		void userWithoutProfile_ChecksDefault() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<Boolean> result = insertTestUser(SYSTEM_CLIENT_ID, "nopr_" + ts,
					"nopr_" + ts + "@test.com", "password123")
					.flatMap(userId -> profileDAO.checkIfUserHasAnyProfile(userId, "appbuilder"));

			// Result depends on whether there are default profiles in the test DB
			StepVerifier.create(result)
					.assertNext(hasProfile -> assertNotNull(hasProfile))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAssignedProfileIds()")
	class GetAssignedProfileIdsTests {

		@Test
		@DisplayName("user with profile returns assigned IDs")
		void userWithProfile_ReturnsAssignedIds() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<List<ULong>> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> getAppbuilderAppId()
							.flatMap(appId -> insertTestUser(SYSTEM_CLIENT_ID, "asgnprof_" + ts,
									"asgnprof_" + ts + "@test.com", "password123")
									.flatMap(userId -> insertProfileUser(profileId, userId)
											.then(profileDAO.getAssignedProfileIds(userId, appId)
													.collectList()))));

			StepVerifier.create(result)
					.assertNext(ids -> {
						assertNotNull(ids);
						assertFalse(ids.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("user without profile returns empty")
		void userWithoutProfile_ReturnsEmpty() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<List<ULong>> result = getAppbuilderAppId()
					.flatMap(appId -> insertTestUser(SYSTEM_CLIENT_ID, "noasgnp_" + ts,
							"noasgnp_" + ts + "@test.com", "password123")
							.flatMap(userId -> profileDAO.getAssignedProfileIds(userId, appId)
									.collectList()));

			StepVerifier.create(result)
					.assertNext(ids -> {
						assertNotNull(ids);
						assertTrue(ids.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("null appId returns all assigned profile IDs")
		void nullAppId_ReturnsAll() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<List<ULong>> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> insertTestUser(SYSTEM_CLIENT_ID, "allprof_" + ts,
							"allprof_" + ts + "@test.com", "password123")
							.flatMap(userId -> insertProfileUser(profileId, userId)
									.then(profileDAO.getAssignedProfileIds(userId, null)
											.collectList())));

			StepVerifier.create(result)
					.assertNext(ids -> {
						assertNotNull(ids);
						assertFalse(ids.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("hasAccessToProfiles()")
	class HasAccessToProfilesTests {

		@Test
		@DisplayName("system client has access to its own profiles")
		void systemClient_HasAccess() {
			Mono<Boolean> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> profileDAO.hasAccessToProfiles(
							SYSTEM_CLIENT_ID, Set.of(profileId)));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		@DisplayName("new business client does not have access to system profiles")
		void newBusClient_NoAccessToSystemProfiles() {
			Mono<Boolean> result = insertTestClient("PRFBUZ", "Profile Bus Client", "BUS")
					.flatMap(clientId -> getAppbuilderOwnerProfileId()
							.flatMap(profileId -> profileDAO.hasAccessToProfiles(
									clientId, Set.of(profileId))));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}

		@Test
		@DisplayName("client with app access has access to profiles of that app")
		void clientWithAppAccess_HasAccess() {
			Mono<Boolean> result = insertTestClient("PRFACC", "Profile Access Client", "BUS")
					.flatMap(clientId -> getAppbuilderAppId()
							.flatMap(appId -> insertAppAccess(clientId, appId)
									.then(getAppbuilderOwnerProfileId()
											.flatMap(profileId -> profileDAO.hasAccessToProfiles(
													clientId, Set.of(profileId))))));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		@DisplayName("empty profileIds returns true")
		void emptyProfileIds_ReturnsTrue() {
			// hasAccessToProfiles with empty set: the finalTuple.getT2() will be empty => returns true
			Mono<Boolean> result = profileDAO.hasAccessToProfiles(SYSTEM_CLIENT_ID, Set.of());

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUserAppHavingProfile()")
	class GetUserAppHavingProfileTests {

		@Test
		@DisplayName("user with profile returns app ID")
		void userWithProfile_ReturnsAppId() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<ULong> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> insertTestUser(SYSTEM_CLIENT_ID, "apphav_" + ts,
							"apphav_" + ts + "@test.com", "password123")
							.flatMap(userId -> insertProfileUser(profileId, userId)
									.then(profileDAO.getUserAppHavingProfile(userId))));

			StepVerifier.create(result)
					.assertNext(appId -> assertNotNull(appId))
					.verifyComplete();
		}

		@Test
		@DisplayName("user without profile returns empty")
		void userWithoutProfile_ReturnsEmpty() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<ULong> result = insertTestUser(SYSTEM_CLIENT_ID, "noapp_" + ts,
					"noapp_" + ts + "@test.com", "password123")
					.flatMap(userId -> profileDAO.getUserAppHavingProfile(userId));

			StepVerifier.create(result)
					.verifyComplete();
		}

		@Test
		@DisplayName("null userId returns empty")
		void nullUserId_ReturnsEmpty() {
			StepVerifier.create(profileDAO.getUserAppHavingProfile(null))
					.verifyComplete();
		}
	}

	// ========================================================================
	// New tests for uncovered methods
	// ========================================================================

	@Nested
	@DisplayName("DAO-level create/readById/update/delete overrides (error cases)")
	class DaoOverrideErrorTests {

		@Test
		@DisplayName("create() throws GenericException")
		void create_ThrowsError() {
			Profile profile = new Profile();
			profile.setName("Test");
			profile.setClientId(SYSTEM_CLIENT_ID);

			StepVerifier.create(profileDAO.create(profile))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("readById() throws GenericException")
		void readById_ThrowsError() {
			StepVerifier.create(profileDAO.readById(ULong.valueOf(1)))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("update(entity) throws GenericException")
		void updateEntity_ThrowsError() {
			Profile profile = new Profile();
			profile.setId(ULong.valueOf(1));
			profile.setName("Test");

			StepVerifier.create(profileDAO.update(profile))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("update(id, map) throws GenericException")
		void updateIdMap_ThrowsError() {
			StepVerifier.create(profileDAO.update(ULong.valueOf(1), Map.of("name", "Test")))
					.expectError(GenericException.class)
					.verify();
		}

		@Test
		@DisplayName("delete(id) throws GenericException")
		void deleteId_ThrowsError() {
			StepVerifier.create(profileDAO.delete(ULong.valueOf(1)))
					.expectError(GenericException.class)
					.verify();
		}
	}

	@Nested
	@DisplayName("readInternal()")
	class ReadInternalTests {

		@Test
		@DisplayName("readInternal returns profile by ID")
		void existingProfile_ReturnsProfile() {
			Mono<Profile> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> profileDAO.readInternal(profileId));

			StepVerifier.create(result)
					.assertNext(profile -> {
						assertNotNull(profile);
						assertEquals("Appbuilder Owner", profile.getName());
						assertEquals(SYSTEM_CLIENT_ID, profile.getClientId());
						assertNull(profile.getRootProfileId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("readInternal with non-existent ID returns empty")
		void nonExistentId_ReturnsEmpty() {
			StepVerifier.create(profileDAO.readInternal(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("read(id, hierarchy)")
	class ReadWithHierarchyTests {

		@Test
		@DisplayName("read root profile owned by same client returns profile directly")
		void rootProfileOwnedBySameClient_ReturnsDirectly() {
			ClientHierarchy hierarchy = systemHierarchy();

			Mono<Profile> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> profileDAO.read(profileId, hierarchy));

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(profile -> {
						assertNotNull(profile);
						assertEquals("Appbuilder Owner", profile.getName());
						assertEquals(SYSTEM_CLIENT_ID, profile.getClientId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("read child profile resolves through root profile")
		void childProfile_ResolvesViaRoot() {
			Mono<Profile> result = getAppbuilderAppId()
					.flatMap(appId -> getAppbuilderOwnerProfileId()
							.flatMap(rootProfileId -> insertTestClient("CHLDRD", "Child Read Client", "BUS")
									.flatMap(childClientId -> insertClientHierarchy(childClientId,
											SYSTEM_CLIENT_ID, null, null, null)
											.then(insertTestProfile(childClientId, appId,
													"Child Owner Override", "Overridden desc",
													rootProfileId, false, null))
											.then(Mono.defer(() -> {
												ClientHierarchy hierarchy = childHierarchy(childClientId);
												return profileDAO.read(rootProfileId, hierarchy);
											})))));

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(profile -> {
						assertNotNull(profile);
						// The child override should be applied on top of root
						assertNotNull(profile.getName());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readRootProfile()")
	class ReadRootProfileTests {

		@Test
		@DisplayName("readRootProfile with no child profiles returns root profile")
		void noChildProfiles_ReturnsRoot() {
			ClientHierarchy hierarchy = systemHierarchy();

			Mono<Profile> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> profileDAO.readRootProfile(profileId, hierarchy, true));

			StepVerifier.create(result)
					.assertNext(profile -> {
						assertNotNull(profile);
						assertEquals("Appbuilder Owner", profile.getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("readRootProfile with child override applies overlay")
		void withChildOverride_AppliesOverlay() {
			Mono<Profile> result = getAppbuilderAppId()
					.flatMap(appId -> getAppbuilderOwnerProfileId()
							.flatMap(rootProfileId -> insertTestClient("CHLDRT", "Child Root Client", "BUS")
									.flatMap(childClientId -> insertClientHierarchy(childClientId,
											SYSTEM_CLIENT_ID, null, null, null)
											.then(insertTestProfile(childClientId, appId,
													"Overridden Name", "Overridden Description",
													rootProfileId, false, null))
											.then(Mono.defer(() -> {
												ClientHierarchy hierarchy = childHierarchy(childClientId);
												return profileDAO.readRootProfile(rootProfileId,
														hierarchy, true);
											})))));

			StepVerifier.create(result)
					.assertNext(profile -> {
						assertNotNull(profile);
						// Child overlay should have been applied; the name should be the overridden one
						assertEquals("Overridden Name", profile.getName());
						assertEquals("Overridden Description", profile.getDescription());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createUpdateProfile()")
	class CreateUpdateProfileTests {

		@Test
		@DisplayName("create new profile (no ID) inserts and returns profile")
		void createNewProfile_Succeeds() {
			Mono<Profile> result = getAppbuilderAppId()
					.flatMap(appId -> {
						Profile profile = new Profile();
						profile.setAppId(appId);
						profile.setClientId(SYSTEM_CLIENT_ID);
						profile.setName("Test New Profile");
						profile.setDescription("A test profile");
						profile.setArrangement(Map.of());

						ClientHierarchy hierarchy = systemHierarchy();
						return profileDAO.createUpdateProfile(profile, ULong.valueOf(1), hierarchy);
					});

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(profile -> {
						assertNotNull(profile);
						assertNotNull(profile.getId());
						assertEquals("Test New Profile", profile.getName());
						assertEquals(SYSTEM_CLIENT_ID, profile.getClientId());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("create profile with roles in arrangement creates role relations")
		void createWithRoles_CreatesRoleRelations() {
			Mono<Profile> result = getAppbuilderAppId()
					.flatMap(appId -> insertTestRole(SYSTEM_CLIENT_ID, appId, "TestCreateRole")
							.flatMap(roleId -> {
								Profile profile = new Profile();
								profile.setAppId(appId);
								profile.setClientId(SYSTEM_CLIENT_ID);
								profile.setName("Profile With Roles");
								profile.setDescription("Has roles");
								profile.setArrangement(
										Map.of("r1", Map.of("roleId", roleId.toString())));

								ClientHierarchy hierarchy = systemHierarchy();
								return profileDAO.createUpdateProfile(profile, ULong.valueOf(1),
										hierarchy);
							}));

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(profile -> {
						assertNotNull(profile);
						assertNotNull(profile.getId());
						assertEquals("Profile With Roles", profile.getName());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("update existing profile (same client, has ID) updates and returns profile")
		void updateExistingProfile_Succeeds() {
			Mono<Profile> result = getAppbuilderAppId()
					.flatMap(appId -> {
						// First, create a profile
						Profile profile = new Profile();
						profile.setAppId(appId);
						profile.setClientId(SYSTEM_CLIENT_ID);
						profile.setName("Profile To Update");
						profile.setDescription("Original desc");
						profile.setArrangement(Map.of());

						ClientHierarchy hierarchy = systemHierarchy();
						return profileDAO.createUpdateProfile(profile, ULong.valueOf(1), hierarchy)
								.flatMap(created -> {
									// Now update it
									created.setName("Updated Profile Name");
									created.setDescription("Updated desc");
									created.setArrangement(Map.of());
									return profileDAO.createUpdateProfile(created,
											ULong.valueOf(1), hierarchy);
								});
					});

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(profile -> {
						assertNotNull(profile);
						assertEquals("Updated Profile Name", profile.getName());
						assertEquals("Updated desc", profile.getDescription());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("create child profile (different client, with existing ID) creates override")
		void createChildProfile_CreatesOverride() {
			Mono<Profile> result = getAppbuilderAppId()
					.flatMap(appId -> {
						// Create root profile first
						Profile rootProfile = new Profile();
						rootProfile.setAppId(appId);
						rootProfile.setClientId(SYSTEM_CLIENT_ID);
						rootProfile.setName("Root For Child");
						rootProfile.setDescription("Root desc");
						rootProfile.setArrangement(Map.of());

						ClientHierarchy systemH = systemHierarchy();
						return profileDAO.createUpdateProfile(rootProfile, ULong.valueOf(1), systemH)
								.flatMap(created -> insertTestClient("CHLDCU", "Child CU Client", "BUS")
										.flatMap(childClientId -> insertClientHierarchy(childClientId,
												SYSTEM_CLIENT_ID, null, null, null)
												.then(Mono.defer(() -> {
													// Create child override (different client, existing
													// ID)
													Profile childProfile = new Profile();
													childProfile.setId(created.getId());
													childProfile.setAppId(appId);
													childProfile.setClientId(childClientId);
													childProfile.setName("Child Override");
													childProfile.setDescription("Child desc");
													childProfile.setArrangement(Map.of());

													ClientHierarchy childH = childHierarchy(
															childClientId);
													return profileDAO.createUpdateProfile(
															childProfile, ULong.valueOf(1), childH);
												}))));
					});

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(profile -> {
						assertNotNull(profile);
						assertEquals("Child Override", profile.getName());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("delete(profileId, hierarchy)")
	class DeleteWithHierarchyTests {

		@Test
		@DisplayName("delete root profile with no children succeeds")
		void deleteRootProfile_NoChildren_Succeeds() {
			Mono<Integer> result = getAppbuilderAppId()
					.flatMap(appId -> {
						Profile profile = new Profile();
						profile.setAppId(appId);
						profile.setClientId(SYSTEM_CLIENT_ID);
						profile.setName("Profile To Delete");
						profile.setDescription("Will be deleted");
						profile.setArrangement(Map.of());

						ClientHierarchy hierarchy = systemHierarchy();
						return profileDAO.createUpdateProfile(profile, ULong.valueOf(1), hierarchy)
								.flatMap(created -> profileDAO.delete(created.getId(), hierarchy));
					});

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(count -> assertTrue(count > 0))
					.verifyComplete();
		}

		@Test
		@DisplayName("delete profile that has children returns 0 (cannot delete)")
		void deleteProfileWithChildren_ReturnsZero() {
			Mono<Integer> result = getAppbuilderAppId()
					.flatMap(appId -> {
						Profile rootProfile = new Profile();
						rootProfile.setAppId(appId);
						rootProfile.setClientId(SYSTEM_CLIENT_ID);
						rootProfile.setName("Root No Delete");
						rootProfile.setDescription("Has children");
						rootProfile.setArrangement(Map.of());

						ClientHierarchy hierarchy = systemHierarchy();
						return profileDAO.createUpdateProfile(rootProfile, ULong.valueOf(1), hierarchy)
								.flatMap(created -> insertTestClient("CHLDDL", "Child Delete Client", "BUS")
										.flatMap(childClientId -> insertClientHierarchy(childClientId,
												SYSTEM_CLIENT_ID, null, null, null)
												.then(insertTestProfile(childClientId, appId,
														"Child Of Root", null,
														created.getId(), false, null))
												.then(profileDAO.delete(created.getId(), hierarchy))));
					});

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(count -> assertEquals(0, count))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("checkProfileAppAccess()")
	class CheckProfileAppAccessTests {

		@Test
		@DisplayName("profile with matching app access returns true")
		void profileWithAppAccess_ReturnsTrue() {
			Mono<Boolean> result = insertTestClient("PRFAPA", "ProfileAppA Client", "BUS")
					.flatMap(clientId -> getAppbuilderAppId()
							.flatMap(appId -> insertAppAccess(clientId, appId)
									.then(getAppbuilderOwnerProfileId()
											.flatMap(profileId -> profileDAO.checkProfileAppAccess(
													profileId, clientId)))));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		@DisplayName("profile without app access returns false")
		void profileWithoutAppAccess_ReturnsFalse() {
			Mono<Boolean> result = insertTestClient("PRFAPB", "ProfileAppB Client", "BUS")
					.flatMap(clientId -> getAppbuilderOwnerProfileId()
							.flatMap(profileId -> profileDAO.checkProfileAppAccess(
									profileId, clientId)));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertFalse(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("restrictClient()")
	class RestrictClientTests {

		@Test
		@DisplayName("restricting a client for a profile inserts restriction record")
		void restrictClient_InsertsRestriction() {
			Mono<Boolean> result = insertTestClient("PRFRES", "Profile Restrict Client", "BUS")
					.flatMap(clientId -> getAppbuilderOwnerProfileId()
							.flatMap(profileId -> profileDAO.restrictClient(profileId, clientId)));

			StepVerifier.create(result)
					.assertNext(success -> assertTrue(success))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("isBeingUsedByManagingClients()")
	class IsBeingUsedByManagingClientsTests {

		@Test
		@DisplayName("profile not used by managing clients returns false")
		void notUsedByManagingClients_ReturnsFalse() {
			Mono<Boolean> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> profileDAO.isBeingUsedByManagingClients(
							SYSTEM_CLIENT_ID, profileId, null));

			StepVerifier.create(result)
					.assertNext(isUsed -> assertFalse(isUsed))
					.verifyComplete();
		}

		@Test
		@DisplayName("profile used by managing client hierarchy returns true")
		void usedByManagingClients_ReturnsTrue() {
			Mono<Boolean> result = getAppbuilderAppId()
					.flatMap(appId -> getAppbuilderOwnerProfileId()
							.flatMap(rootProfileId -> insertTestClient("CHLDMG", "Child Managed Client", "BUS")
									.flatMap(childClientId -> insertClientHierarchy(childClientId,
											SYSTEM_CLIENT_ID, SYSTEM_CLIENT_ID, SYSTEM_CLIENT_ID,
											SYSTEM_CLIENT_ID)
											.then(insertTestProfile(childClientId, appId,
													"Managed Child Profile", null,
													rootProfileId, false, null))
											.then(profileDAO.isBeingUsedByManagingClients(
													SYSTEM_CLIENT_ID, rootProfileId, null)))));

			StepVerifier.create(result)
					.assertNext(isUsed -> assertTrue(isUsed))
					.verifyComplete();
		}

		@Test
		@DisplayName("with explicit rootProfileId uses rootProfileId")
		void withExplicitRootProfileId_UsesIt() {
			Mono<Boolean> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> profileDAO.isBeingUsedByManagingClients(
							SYSTEM_CLIENT_ID, ULong.valueOf(999999), profileId));

			// Even with non-existent profileId, rootProfileId is used instead
			StepVerifier.create(result)
					.assertNext(isUsed -> assertNotNull(isUsed))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUsersForProfiles()")
	class GetUsersForProfilesTests {

		@Test
		@DisplayName("returns user IDs assigned to given profiles")
		void usersAssignedToProfiles_ReturnsUserIds() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<List<ULong>> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> getAppbuilderAppId()
							.flatMap(appId -> insertTestUser(SYSTEM_CLIENT_ID, "profusr_" + ts,
									"profusr_" + ts + "@test.com", "password123")
									.flatMap(userId -> insertProfileUser(profileId, userId)
											.then(profileDAO
													.getUsersForProfiles(appId, List.of(profileId))
													.collectList()))));

			StepVerifier.create(result)
					.assertNext(userIds -> {
						assertNotNull(userIds);
						assertFalse(userIds.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("no users assigned returns empty")
		void noUsersAssigned_ReturnsEmpty() {
			Mono<List<ULong>> result = getAppbuilderAppId()
					.flatMap(appId -> insertTestClient("PRFNUC", "NoUserClient", "BUS")
							.flatMap(clientId -> insertTestProfile(clientId, appId,
									"Empty Profile", null, null, false, "{}")
									.flatMap(profileId -> profileDAO
											.getUsersForProfiles(appId, List.of(profileId))
											.collectList())));

			StepVerifier.create(result)
					.assertNext(userIds -> assertTrue(userIds.isEmpty()))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readAll()")
	class ReadAllTests {

		@Test
		@DisplayName("readAll returns page of profiles for system client")
		void systemClient_ReturnsPage() {
			ClientHierarchy hierarchy = systemHierarchy();

			Mono<Page<Profile>> result = getAppbuilderAppId()
					.flatMap(appId -> profileDAO.readAll(appId, hierarchy, PageRequest.of(0, 10)));

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertFalse(page.getContent().isEmpty());
						assertTrue(page.getTotalElements() > 0);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("readAll with second page returns empty if only one profile exists")
		void secondPageEmpty_WhenFewProfiles() {
			ClientHierarchy hierarchy = systemHierarchy();

			Mono<Page<Profile>> result = getAppbuilderAppId()
					.flatMap(appId -> profileDAO.readAll(appId, hierarchy, PageRequest.of(100, 10)));

			StepVerifier.create(result
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertNotNull(page);
						assertTrue(page.getContent().isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("hasAccessToRoles(appId, hierarchy, roleIds)")
	class HasAccessToRolesTests {

		@Test
		@DisplayName("system client has access to roles in its own profiles")
		void systemClient_HasAccessToRoles() {
			Mono<Boolean> result = getAppbuilderAppId()
					.flatMap(appId -> {
						// Get a role from the existing profile
						return databaseClient.sql(
								"SELECT pr.ROLE_ID FROM security_profile_role pr "
										+ "INNER JOIN security_profile p ON p.ID = pr.PROFILE_ID "
										+ "WHERE p.NAME = 'Appbuilder Owner' LIMIT 1")
								.map(row -> ULong.valueOf(row.get("ROLE_ID", Long.class)))
								.one()
								.flatMap(roleId -> {
									ClientHierarchy hierarchy = systemHierarchy();
									return profileDAO.hasAccessToRoles(appId, hierarchy,
											Set.of(roleId));
								});
					});

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		@DisplayName("client with own role has access")
		void clientWithOwnRole_HasAccess() {
			Mono<Boolean> result = insertTestClient("PRFROL", "Profile Role Client", "BUS")
					.flatMap(clientId -> getAppbuilderAppId()
							.flatMap(appId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID,
									null, null, null)
									.then(insertTestRole(clientId, appId, "OwnClientRole"))
									.flatMap(roleId -> {
										ClientHierarchy hierarchy = childHierarchy(clientId);
										return profileDAO.hasAccessToRoles(appId, hierarchy,
												Set.of(roleId));
									})));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		@DisplayName("empty roleIds returns true")
		void emptyRoleIds_ReturnsTrue() {
			ClientHierarchy hierarchy = systemHierarchy();

			Mono<Boolean> result = getAppbuilderAppId()
					.flatMap(appId -> profileDAO.hasAccessToRoles(appId, hierarchy, Set.of()));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}

		@Test
		@DisplayName("hasAccessToRoles with Profile overload works correctly")
		void profileOverload_WorksCorrectly() {
			Mono<Boolean> result = getAppbuilderAppId()
					.flatMap(appId -> databaseClient.sql(
							"SELECT pr.ROLE_ID FROM security_profile_role pr "
									+ "INNER JOIN security_profile p ON p.ID = pr.PROFILE_ID "
									+ "WHERE p.NAME = 'Appbuilder Owner' LIMIT 1")
							.map(row -> ULong.valueOf(row.get("ROLE_ID", Long.class)))
							.one()
							.flatMap(roleId -> {
								Profile profile = new Profile();
								profile.setArrangement(
										Map.of("r1", Map.of("roleId", roleId.toString())));

								ClientHierarchy hierarchy = systemHierarchy();
								return profileDAO.hasAccessToRoles(appId, hierarchy, profile);
							}));

			StepVerifier.create(result)
					.assertNext(hasAccess -> assertTrue(hasAccess))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getProfileAuthorities()")
	class GetProfileAuthoritiesTests {

		@Test
		@DisplayName("returns authorities for Appbuilder Owner profile")
		void appbuilderOwnerProfile_ReturnsAuthorities() {
			ClientHierarchy hierarchy = systemHierarchy();

			Mono<List<String>> result = getAppbuilderOwnerProfileId()
					.flatMap(profileId -> profileDAO.getProfileAuthorities(profileId, hierarchy));

			StepVerifier.create(result)
					.assertNext(authorities -> {
						assertNotNull(authorities);
						assertFalse(authorities.isEmpty());
						// Should contain role names
						assertTrue(authorities.stream()
								.anyMatch(a -> a.startsWith("Authorities.")));
						// Should contain profile name
						assertTrue(authorities.stream()
								.anyMatch(a -> a.contains("PROFILE_")));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("custom profile with role and permission returns correct authority names")
		void customProfileWithRoleAndPermission_ReturnsCorrectAuthorities() {
			Mono<List<String>> result = getAppbuilderAppId()
					.flatMap(appId -> insertTestRole(SYSTEM_CLIENT_ID, appId, "CustomTestRole")
							.flatMap(roleId -> insertTestPermission(SYSTEM_CLIENT_ID, appId,
									"CustomTestPerm")
									.flatMap(permId -> insertRolePermission(roleId, permId)
											.then(Mono.defer(() -> {
												String arrangement = String.format(
														"{\"r1\": {\"roleId\": \"%s\"}}",
														roleId.toString());
												return insertTestProfile(SYSTEM_CLIENT_ID, appId,
														"Custom Auth Profile", "Profile with custom auth",
														null, false, arrangement);
											}))
											.flatMap(profileId -> insertProfileRole(profileId, roleId, 0)
													.then(Mono.defer(() -> {
														ClientHierarchy hierarchy = systemHierarchy();
														return profileDAO.getProfileAuthorities(
																profileId, hierarchy);
													}))))));

			StepVerifier.create(result)
					.assertNext(authorities -> {
						assertNotNull(authorities);
						assertFalse(authorities.isEmpty());
						// Should contain the custom role
						assertTrue(authorities.stream()
								.anyMatch(a -> a.contains("ROLE_CustomTestRole")));
						// Should contain the custom permission
						assertTrue(authorities.stream()
								.anyMatch(a -> a.contains("CustomTestPerm")));
						// Should contain the profile name
						assertTrue(authorities.stream()
								.anyMatch(a -> a.contains("PROFILE_Custom_Auth_Profile")));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("profile with excluded role does not include it in authorities")
		void profileWithExcludedRole_ExcludesFromAuthorities() {
			Mono<List<String>> result = getAppbuilderAppId()
					.flatMap(appId -> insertTestRole(SYSTEM_CLIENT_ID, appId, "IncludedRole")
							.flatMap(includedRoleId -> insertTestRole(SYSTEM_CLIENT_ID, appId,
									"ExcludedRole")
									.flatMap(excludedRoleId -> {
										String arrangement = String.format(
												"{\"r1\": {\"roleId\": \"%s\"}, \"r2\": {\"roleId\": \"%s\"}}",
												includedRoleId.toString(), excludedRoleId.toString());
										return insertTestProfile(SYSTEM_CLIENT_ID, appId,
												"Excl Auth Profile", null,
												null, false, arrangement)
												.flatMap(profileId -> insertProfileRole(profileId,
														includedRoleId, 0)
														.then(insertProfileRole(profileId,
																excludedRoleId, 1))
														.then(Mono.defer(() -> {
															ClientHierarchy hierarchy = systemHierarchy();
															return profileDAO.getProfileAuthorities(
																	profileId, hierarchy);
														})));
									})));

			StepVerifier.create(result)
					.assertNext(authorities -> {
						assertNotNull(authorities);
						// Included role should be present
						assertTrue(authorities.stream()
								.anyMatch(a -> a.contains("ROLE_IncludedRole")));
						// Excluded role should NOT be present
						assertFalse(authorities.stream()
								.anyMatch(a -> a.contains("ROLE_ExcludedRole")));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("profile with sub-roles includes sub-role authorities")
		void profileWithSubRoles_IncludesSubRoleAuthorities() {
			Mono<List<String>> result = getAppbuilderAppId()
					.flatMap(appId -> insertTestRole(SYSTEM_CLIENT_ID, appId, "ParentTestRole")
							.flatMap(parentRoleId -> insertTestRole(SYSTEM_CLIENT_ID, appId,
									"SubTestRole")
									.flatMap(subRoleId -> insertRoleRole(parentRoleId, subRoleId)
											.then(Mono.defer(() -> {
												String arrangement = String.format(
														"{\"r1\": {\"roleId\": \"%s\"}}",
														parentRoleId.toString());
												return insertTestProfile(SYSTEM_CLIENT_ID, appId,
														"SubRole Auth Profile", null,
														null, false, arrangement);
											}))
											.flatMap(profileId -> insertProfileRole(profileId,
													parentRoleId, 0)
													.then(Mono.defer(() -> {
														ClientHierarchy hierarchy = systemHierarchy();
														return profileDAO.getProfileAuthorities(
																profileId, hierarchy);
													}))))));

			StepVerifier.create(result)
					.assertNext(authorities -> {
						assertNotNull(authorities);
						// Should contain parent role
						assertTrue(authorities.stream()
								.anyMatch(a -> a.contains("ROLE_ParentTestRole")));
						// Should contain sub-role
						assertTrue(authorities.stream()
								.anyMatch(a -> a.contains("ROLE_SubTestRole")));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getRolesForAssignmentInApp()")
	class GetRolesForAssignmentInAppTests {

		@Test
		@DisplayName("returns roles assigned to profiles for the app")
		void systemClient_ReturnsRoles() {
			ClientHierarchy hierarchy = systemHierarchy();

			Mono<List<?>> result = getAppbuilderAppId()
					.flatMap(appId -> profileDAO.getRolesForAssignmentInApp(appId, hierarchy));

			StepVerifier.create(result)
					.assertNext(roles -> {
						assertNotNull(roles);
						assertFalse(roles.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("restricted profiles are used when available")
		void restrictedProfiles_TakePrecedence() {
			Mono<List<?>> result = insertTestClient("PRFRLR", "Restrict Roles Client", "BUS")
					.flatMap(clientId -> getAppbuilderAppId()
							.flatMap(appId -> insertClientHierarchy(clientId, SYSTEM_CLIENT_ID,
									null, null, null)
									.then(insertAppAccess(clientId, appId))
									.then(getAppbuilderOwnerProfileId()
											.flatMap(profileId -> databaseClient.sql(
													"INSERT INTO security_profile_client_restriction (PROFILE_ID, CLIENT_ID) VALUES (:profileId, :clientId)")
													.bind("profileId", profileId.longValue())
													.bind("clientId", clientId.longValue())
													.then()
													.then(Mono.defer(() -> {
														ClientHierarchy hierarchy = childHierarchy(
																clientId);
														return profileDAO
																.getRolesForAssignmentInApp(
																		appId, hierarchy);
													}))))));

			StepVerifier.create(result)
					.assertNext(roles -> {
						assertNotNull(roles);
						// Should return roles from the restricted profile
						assertFalse(roles.isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAppProfileHavingAuthorities()")
	class GetAppProfileHavingAuthoritiesTests {

		@Test
		@DisplayName("returns profile IDs that have matching authorities")
		void matchingAuthorities_ReturnsProfileIds() {
			ClientHierarchy hierarchy = systemHierarchy();

			// The Appbuilder Owner profile should have the Owner role authority
			Mono<List<ULong>> result = getAppbuilderAppId()
					.flatMap(appId -> profileDAO.getAppProfileHavingAuthorities(appId, hierarchy,
							List.of("Authorities.ROLE_Owner")));

			StepVerifier.create(result)
					.assertNext(profileIds -> {
						assertNotNull(profileIds);
						assertFalse(profileIds.isEmpty());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("non-matching authorities returns empty list")
		void nonMatchingAuthorities_ReturnsEmpty() {
			ClientHierarchy hierarchy = systemHierarchy();

			Mono<List<ULong>> result = getAppbuilderAppId()
					.flatMap(appId -> profileDAO.getAppProfileHavingAuthorities(appId, hierarchy,
							List.of("Authorities.NonExistentAuthority_XYZ_12345")));

			StepVerifier.create(result)
					.assertNext(profileIds -> {
						assertNotNull(profileIds);
						assertTrue(profileIds.isEmpty());
					})
					.verifyComplete();
		}
	}
}
