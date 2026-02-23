package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.Profile;
import com.fincity.security.service.ProfileService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProfileServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ProfileService profileService;

	private ContextAuthentication systemAuth;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient
						.sql("DELETE FROM security_profile_user WHERE PROFILE_ID IN (SELECT ID FROM security_profile WHERE ID > 0 AND CLIENT_ID > 1)")
						.then())
				.then(databaseClient
						.sql("DELETE FROM security_profile_role WHERE PROFILE_ID IN (SELECT ID FROM security_profile WHERE ID > 0 AND CLIENT_ID > 1)")
						.then())
				.then(databaseClient.sql("DELETE FROM security_profile WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_v2_role WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient
						.sql("DELETE FROM security_app_access WHERE APP_ID IN (SELECT ID FROM security_app WHERE CLIENT_ID > 1 OR APP_CODE LIKE 'profapp%')")
						.then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE LIKE 'profapp%'").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	@Test
	void createAndReadProfile_WithValidData_ReturnsPersistedProfile() {

		Mono<Profile> result = insertTestClient("PRFBUS1", "Profile Business One", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestApp(ULong.valueOf(1), "profapp1", "Profile Test App"))
						.flatMap(appId -> databaseClient.sql(
								"INSERT INTO security_app_access (CLIENT_ID, APP_ID, EDIT_ACCESS) VALUES (:clientId, :appId, 0)")
								.bind("clientId", clientId.longValue())
								.bind("appId", appId.longValue())
								.then()
								.then(Mono.just(appId))
								.flatMap(aid -> {
									Profile profile = new Profile();
									profile.setClientId(clientId);
									profile.setAppId(aid);
									profile.setName("TestProfile");
									profile.setDescription("A test profile for integration testing");
									profile.setArrangement(new HashMap<>());
									return profileService.create(profile);
								})))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(createdProfile -> {
					assertThat(createdProfile).isNotNull();
					assertThat(createdProfile.getId()).isNotNull();
					assertThat(createdProfile.getName()).isEqualTo("TestProfile");
					assertThat(createdProfile.getDescription()).isEqualTo("A test profile for integration testing");
				})
				.verifyComplete();
	}

	@Test
	void profileWithRoleAssignment_GetProfileAuthorities_ReturnsAuthorities() {

		Mono<List<String>> result = insertTestClient("PRFBUS2", "Profile Business Two", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestApp(ULong.valueOf(1), "profapp2", "Profile Test App 2"))
						.flatMap(appId -> databaseClient.sql(
								"INSERT INTO security_app_access (CLIENT_ID, APP_ID, EDIT_ACCESS) VALUES (:clientId, :appId, 0)")
								.bind("clientId", clientId.longValue())
								.bind("appId", appId.longValue())
								.then()
								.then(Mono.just(appId)))
						.flatMap(appId -> {
							Profile profile = new Profile();
							profile.setClientId(clientId);
							profile.setAppId(appId);
							profile.setName("RoleProfile");
							profile.setDescription("Profile with roles");
							profile.setArrangement(new HashMap<>());
							return profileService.create(profile)
									.flatMap(createdProfile -> databaseClient.sql(
											"INSERT INTO security_v2_role (CLIENT_ID, APP_ID, NAME, SHORT_NAME, DESCRIPTION) VALUES (:clientId, :appId, :name, :shortName, :desc)")
											.bind("clientId", 1L)
											.bind("appId", appId.longValue())
											.bind("name", "TestRole")
											.bind("shortName", "TESTROLE")
											.bind("desc", "A test role")
											.filter(s -> s.returnGeneratedValues("ID"))
											.map(row -> ULong.valueOf(row.get("ID", Long.class)))
											.one()
											.flatMap(roleId -> databaseClient.sql(
													"INSERT INTO security_profile_role (PROFILE_ID, ROLE_ID, EXCLUDE) VALUES (:profileId, :roleId, 0)")
													.bind("profileId",
															createdProfile.getId().longValue())
													.bind("roleId", roleId.longValue())
													.then())
											.then(insertTestUser(clientId, "profauthuser",
													"profauthuser@test.com", "fincity@123")
													.flatMap(userId -> databaseClient.sql(
															"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
															.bind("profileId",
																	createdProfile.getId()
																			.longValue())
															.bind("userId", userId.longValue())
															.then()
															.then(profileService
																	.getProfileAuthorities(
																			"profapp2",
																			clientId,
																			userId)))));
						}))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(authorities -> {
					assertThat(authorities).isNotNull();
					assertThat(authorities).isNotEmpty();
				})
				.verifyComplete();
	}

	@Test
	void userProfileAssignment_CheckIfUserHasAnyProfile_ReturnsTrue() {

		Mono<Boolean> result = insertTestClient("PRFBUS3", "Profile Business Three", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestApp(ULong.valueOf(1), "profapp3", "Profile Test App 3"))
						.flatMap(appId -> databaseClient.sql(
								"INSERT INTO security_app_access (CLIENT_ID, APP_ID, EDIT_ACCESS) VALUES (:clientId, :appId, 0)")
								.bind("clientId", clientId.longValue())
								.bind("appId", appId.longValue())
								.then()
								.then(Mono.just(appId)))
						.flatMap(appId -> {
							Profile profile = new Profile();
							profile.setClientId(clientId);
							profile.setAppId(appId);
							profile.setName("UserAssignProfile");
							profile.setDescription("Profile for user assignment test");
							profile.setArrangement(new HashMap<>());
							return profileService.create(profile)
									.flatMap(createdProfile -> insertTestUser(clientId, "profileuser1",
											"profileuser1@test.com", "fincity@123")
											.flatMap(userId -> databaseClient.sql(
													"INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
													.bind("profileId",
															createdProfile.getId().longValue())
													.bind("userId", userId.longValue())
													.then()
													.then(profileService.checkIfUserHasAnyProfile(
															userId, "profapp3"))));
						}))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(hasProfile -> assertThat(hasProfile).isTrue())
				.verifyComplete();
	}

	@Test
	void getUsersForProfiles_WithAssignedUsers_ReturnsCorrectUserIds() {

		Mono<List<ULong>> result = insertTestClient("PRFBUS4", "Profile Business Four", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestApp(ULong.valueOf(1), "profapp4", "Profile Test App 4"))
						.flatMap(appId -> databaseClient.sql(
								"INSERT INTO security_app_access (CLIENT_ID, APP_ID, EDIT_ACCESS) VALUES (:clientId, :appId, 0)")
								.bind("clientId", clientId.longValue())
								.bind("appId", appId.longValue())
								.then()
								.then(Mono.just(appId)))
						.flatMap(appId -> {
							Profile profile1 = new Profile();
							profile1.setClientId(clientId);
							profile1.setAppId(appId);
							profile1.setName("ProfileA");
							profile1.setDescription("First profile");
							profile1.setArrangement(new HashMap<>());

							return profileService.create(profile1)
									.flatMap(createdProfile1 -> {
										Profile profile2 = new Profile();
										profile2.setClientId(clientId);
										profile2.setAppId(appId);
										profile2.setName("ProfileB");
										profile2.setDescription("Second profile");
										profile2.setArrangement(new HashMap<>());

										return profileService.create(profile2)
												.flatMap(createdProfile2 -> insertTestUser(clientId,
														"profuser1", "profuser1@test.com",
														"fincity@123")
														.flatMap(userId1 -> insertTestUser(clientId,
																"profuser2", "profuser2@test.com",
																"fincity@123")
																.flatMap(userId2 -> databaseClient
																		.sql("INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
																		.bind("profileId",
																				createdProfile1
																						.getId()
																						.longValue())
																		.bind("userId",
																				userId1.longValue())
																		.then()
																		.then(databaseClient
																				.sql("INSERT INTO security_profile_user (PROFILE_ID, USER_ID) VALUES (:profileId, :userId)")
																				.bind("profileId",
																						createdProfile2
																								.getId()
																								.longValue())
																				.bind("userId",
																						userId2.longValue())
																				.then())
																		.then(profileService
																				.getUsersForProfiles(
																						appId,
																						List.of(createdProfile1
																								.getId(),
																								createdProfile2
																										.getId()))))));
									});
						}))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(userIds -> {
					assertThat(userIds).isNotNull();
					assertThat(userIds).hasSize(2);
				})
				.verifyComplete();
	}

	@Test
	void deleteProfile_ExistingProfile_RemovesSuccessfully() {

		Mono<Integer> result = insertTestClient("PRFBUS5", "Profile Business Five", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestApp(ULong.valueOf(1), "profapp5", "Profile Test App 5"))
						.flatMap(appId -> databaseClient.sql(
								"INSERT INTO security_app_access (CLIENT_ID, APP_ID, EDIT_ACCESS) VALUES (:clientId, :appId, 0)")
								.bind("clientId", clientId.longValue())
								.bind("appId", appId.longValue())
								.then()
								.then(Mono.just(appId)))
						.flatMap(appId -> {
							Profile profile = new Profile();
							profile.setClientId(clientId);
							profile.setAppId(appId);
							profile.setName("ToBeDeleted");
							profile.setDescription("This profile will be deleted");
							profile.setArrangement(new HashMap<>());
							return profileService.create(profile)
									.flatMap(
											createdProfile -> profileService
													.delete(createdProfile.getId()));
						}))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(deleteCount -> assertThat(deleteCount).isEqualTo(1))
				.verifyComplete();
	}

	@Test
	void checkIfUserHasAnyProfile_NoProfileAssigned_ReturnsFalse() {

		Mono<Boolean> result = insertTestClient("PRFBUS6", "Profile Business Six", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestApp(ULong.valueOf(1), "profapp6", "Profile Test App 6"))
						.flatMap(appId -> databaseClient.sql(
								"INSERT INTO security_app_access (CLIENT_ID, APP_ID, EDIT_ACCESS) VALUES (:clientId, :appId, 0)")
								.bind("clientId", clientId.longValue())
								.bind("appId", appId.longValue())
								.then()
								.then(Mono.just(appId)))
						.flatMap(appId -> insertTestUser(clientId, "noprofileuser",
								"noprofileuser@test.com", "fincity@123")
								.flatMap(userId -> profileService.checkIfUserHasAnyProfile(userId,
										"profapp6"))))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(hasProfile -> assertThat(hasProfile).isFalse())
				.verifyComplete();
	}
}
