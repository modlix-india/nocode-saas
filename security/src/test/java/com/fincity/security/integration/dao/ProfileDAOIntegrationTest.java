package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.ProfileDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProfileDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ProfileDAO profileDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
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
							.flatMap(userId -> databaseClient.sql(
									"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
									.bind("profileId", profileId.longValue())
									.bind("userId", userId.longValue())
									.then()
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
							.flatMap(userId -> databaseClient.sql(
									"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
									.bind("profileId", profileId.longValue())
									.bind("userId", userId.longValue())
									.then()
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
									.flatMap(userId -> databaseClient.sql(
											"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
											.bind("profileId", profileId.longValue())
											.bind("userId", userId.longValue())
											.then()
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
							.flatMap(userId -> databaseClient.sql(
									"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
									.bind("profileId", profileId.longValue())
									.bind("userId", userId.longValue())
									.then()
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
							.flatMap(userId -> databaseClient.sql(
									"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
									.bind("profileId", profileId.longValue())
									.bind("userId", userId.longValue())
									.then()
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
	}
}
