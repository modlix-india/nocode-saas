package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.service.ProfileService;
import com.fincity.security.service.UserInviteService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserInviteServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private UserInviteService userInviteService;

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
				.then(databaseClient.sql("DELETE FROM security_user_invite WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user_invite WHERE CLIENT_ID = 1 AND ID > 0").then())
				.then(databaseClient
						.sql("DELETE FROM security_profile_user WHERE PROFILE_ID IN (SELECT ID FROM security_profile WHERE CLIENT_ID > 1)")
						.then())
				.then(databaseClient
						.sql("DELETE FROM security_profile_role WHERE PROFILE_ID IN (SELECT ID FROM security_profile WHERE CLIENT_ID > 1)")
						.then())
				.then(databaseClient.sql("DELETE FROM security_profile WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient
						.sql("DELETE FROM security_app_access WHERE APP_ID IN (SELECT ID FROM security_app WHERE CLIENT_ID > 1 OR APP_CODE LIKE 'invapp%')")
						.then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE APP_CODE LIKE 'invapp%'").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	// Helper to create a UserInvite DTO with basic fields
	private UserInvite createInviteDto(ULong clientId, String email, String userName, String firstName,
			String lastName) {
		UserInvite invite = new UserInvite();
		invite.setClientId(clientId);
		invite.setEmailId(email);
		invite.setUserName(userName);
		invite.setFirstName(firstName);
		invite.setLastName(lastName);
		return invite;
	}

	// Helper to insert a user invite directly via SQL (bypassing service layer)
	private Mono<ULong> insertTestInvite(ULong clientId, String email, String userName, String inviteCode) {
		return databaseClient.sql(
				"INSERT INTO security_user_invite (CLIENT_ID, EMAIL_ID, USER_NAME, FIRST_NAME, LAST_NAME, INVITE_CODE) VALUES (:clientId, :email, :userName, 'Test', 'Invitee', :inviteCode)")
				.bind("clientId", clientId.longValue())
				.bind("email", email)
				.bind("userName", userName)
				.bind("inviteCode", inviteCode)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	@Nested
	@DisplayName("createInvite()")
	class CreateInviteTests {

		@Test
		@DisplayName("new user invite with clientId set - should create invite with generated invite code")
		void newInvite_WithClientId_CreatesInvite() {

			Mono<Map<String, Object>> result = insertTestClient("INVONE", "Invite Client One", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> {
						UserInvite invite = createInviteDto(clientId, "newinvite@test.com", "newinvuser",
								"New", "User");
						return userInviteService.createInvite(invite);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(resultMap -> {
						assertThat(resultMap).containsKey("userRequest");
						assertThat(resultMap).containsEntry("existingUser", Boolean.FALSE);
						Object userRequest = resultMap.get("userRequest");
						assertThat(userRequest).isInstanceOf(UserInvite.class);
						UserInvite created = (UserInvite) userRequest;
						assertThat(created.getId()).isNotNull();
						assertThat(created.getInviteCode()).isNotNull().hasSize(32);
						assertThat(created.getEmailId()).isEqualTo("newinvite@test.com");
						assertThat(created.getUserName()).isEqualTo("newinvuser");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("new user invite without clientId - should default to authenticated user's client")
		void newInvite_WithoutClientId_DefaultsToAuthClient() {

			Mono<Map<String, Object>> result = Mono.defer(() -> {
				UserInvite invite = new UserInvite();
				invite.setEmailId("noClientInv@test.com");
				invite.setUserName("noclntuser");
				invite.setFirstName("NoClient");
				invite.setLastName("User");
				// Do NOT set clientId - should default to system client (1)
				return userInviteService.createInvite(invite);
			}).contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(resultMap -> {
						assertThat(resultMap).containsEntry("existingUser", Boolean.FALSE);
						UserInvite created = (UserInvite) resultMap.get("userRequest");
						assertThat(created.getClientId()).isEqualTo(ULong.valueOf(1));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("invite for existing user without profile - should return empty (switchIfEmpty -> forbidden)")
		void invite_ExistingUser_NoProfile_ReturnsForbidden() {

			Mono<Map<String, Object>> result = insertTestClient("INVTWO", "Invite Client Two", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestUser(clientId, "existuser", "exist@test.com", "fincity@123"))
							.thenReturn(clientId))
					.flatMap(clientId -> {
						// Invite with same email as existing user, but no profileId
						UserInvite invite = createInviteDto(clientId, "exist@test.com", "existuser",
								"Existing", "User");
						return userInviteService.createInvite(invite);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			// addUserProfile returns Mono.empty() when profileId is null, which causes
			// the switchIfEmpty to fire with FORBIDDEN_CREATE
			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == org.springframework.http.HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		@DisplayName("invite for existing user with valid profile - should add profile and return existingUser=true")
		void invite_ExistingUser_WithProfile_AddsProfile() {

			Mono<Map<String, Object>> result = insertTestClient("INVTHR", "Invite Client Three", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(insertTestUser(clientId, "profexist", "profexist@test.com", "fincity@123"))
							.then(insertTestApp(ULong.valueOf(1), "invapp1", "Invite App One"))
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
										profile.setName("InviteProfile");
										profile.setDescription("Profile for invite test");
										profile.setArrangement(new HashMap<>());
										return profileService.create(profile)
												.contextWrite(ReactiveSecurityContextHolder
														.withAuthentication(systemAuth));
									}))
							.flatMap(createdProfile -> {
								UserInvite invite = createInviteDto(clientId, "profexist@test.com",
										"profexist", "Profile", "Exist");
								invite.setProfileId(createdProfile.getId());
								return userInviteService.createInvite(invite);
							}))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(resultMap -> {
						assertThat(resultMap).containsKey("userRequest");
						assertThat(resultMap).containsEntry("existingUser", Boolean.TRUE);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("invite with reportingTo set to user in same client - should succeed")
		void invite_WithReportingTo_SameClient_Succeeds() {

			Mono<Map<String, Object>> result = insertTestClient("INVFOU", "Invite Client Four", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestUser(clientId, "manager1", "manager1@test.com", "fincity@123"))
							.flatMap(managerId -> {
								UserInvite invite = createInviteDto(clientId, "subordinate@test.com",
										"suborduser", "Sub", "Ordinate");
								invite.setReportingTo(managerId);
								return userInviteService.createInvite(invite);
							}))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(resultMap -> {
						assertThat(resultMap).containsEntry("existingUser", Boolean.FALSE);
						UserInvite created = (UserInvite) resultMap.get("userRequest");
						assertThat(created.getReportingTo()).isNotNull();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("invite with reportingTo set to user in different client - should fail with FORBIDDEN")
		void invite_WithReportingTo_DifferentClient_Fails() {

			Mono<Map<String, Object>> result = insertTestClient("INVFIV", "Invite Client Five", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> insertTestClient("INVSIX", "Invite Client Six", "BUS")
							.flatMap(otherClientId -> insertClientHierarchy(otherClientId, ULong.valueOf(1),
									null, null, null)
									.then(insertTestUser(otherClientId, "othermgr", "othermgr@test.com",
											"fincity@123")))
							.flatMap(otherUserId -> {
								UserInvite invite = createInviteDto(clientId, "wrongrpt@test.com",
										"wrongrpt", "Wrong", "Report");
								invite.setReportingTo(otherUserId);
								return userInviteService.createInvite(invite);
							}))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			// The reportingTo check filters out non-matching client, which causes
			// switchIfEmpty -> FORBIDDEN
			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == org.springframework.http.HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		@DisplayName("invite with clientId not managed by authenticated user - should fail with FORBIDDEN")
		void invite_UnmanagedClient_Fails() {

			// Create a business auth that doesn't manage the target client
			Mono<Map<String, Object>> result = insertTestClient("INVSEV", "Invite Client Seven", "BUS")
					.flatMap(busClientId -> insertClientHierarchy(busClientId, ULong.valueOf(1), null, null,
							null)
							.thenReturn(busClientId))
					.flatMap(busClientId -> insertTestClient("INVEIG", "Invite Client Eight", "BUS")
							.flatMap(targetClientId -> insertClientHierarchy(targetClientId,
									ULong.valueOf(1), null, null, null)
									.thenReturn(targetClientId)
									.flatMap(tid -> {
										ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
												busClientId, "INVSEV",
												List.of("Authorities.User_CREATE", "Authorities.Logged_IN"));
										UserInvite invite = createInviteDto(tid,
												"unmgd@test.com", "unmgduser", "Unmanaged", "User");
										return userInviteService.createInvite(invite)
												.contextWrite(ReactiveSecurityContextHolder
														.withAuthentication(busAuth));
									})));

			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == org.springframework.http.HttpStatus.FORBIDDEN)
					.verify();
		}

		@Test
		@DisplayName("invite with valid profile - system client can create invite with profile")
		void invite_WithValidProfile_SystemAuth_Succeeds() {

			Mono<Map<String, Object>> result = insertTestClient("INVNIN", "Invite Client Nine", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(insertTestApp(ULong.valueOf(1), "invapp2", "Invite App Two"))
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
										profile.setName("InviteValidProfile");
										profile.setDescription("Valid profile for invite");
										profile.setArrangement(new HashMap<>());
										return profileService.create(profile)
												.contextWrite(ReactiveSecurityContextHolder
														.withAuthentication(systemAuth));
									}))
							.flatMap(createdProfile -> {
								UserInvite invite = createInviteDto(clientId, "withprof@test.com",
										"withprofuser", "WithProf", "User");
								invite.setProfileId(createdProfile.getId());
								return userInviteService.createInvite(invite);
							}))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(resultMap -> {
						assertThat(resultMap).containsEntry("existingUser", Boolean.FALSE);
						UserInvite created = (UserInvite) resultMap.get("userRequest");
						assertThat(created.getProfileId()).isNotNull();
						assertThat(created.getInviteCode()).isNotNull().hasSize(32);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getUserInvitation()")
	class GetUserInvitationTests {

		@Test
		@DisplayName("valid invite code - should return the invitation")
		void validCode_ReturnsInvitation() {

			String inviteCode = "abc12345678901234567890123456789";

			Mono<UserInvite> result = insertTestClient("GIONE", "GetInv Client One", "BUS")
					.flatMap(clientId -> insertTestInvite(clientId, "getinv@test.com", "getinvuser",
							inviteCode))
					.then(userInviteService.getUserInvitation(inviteCode));

			StepVerifier.create(result)
					.assertNext(invite -> {
						assertThat(invite).isNotNull();
						assertThat(invite.getInviteCode()).isEqualTo(inviteCode);
						assertThat(invite.getEmailId()).isEqualTo("getinv@test.com");
						assertThat(invite.getUserName()).isEqualTo("getinvuser");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("invalid invite code - should return empty")
		void invalidCode_ReturnsEmpty() {

			StepVerifier.create(userInviteService.getUserInvitation("nonexistent_code_123456789012"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("deleteUserInvitation()")
	class DeleteUserInvitationTests {

		@Test
		@DisplayName("existing invite code - should delete and return true")
		void existingCode_DeletesAndReturnsTrue() {

			String inviteCode = "del12345678901234567890123456789";

			Mono<Boolean> result = insertTestClient("DLONE", "Delete Client One", "BUS")
					.flatMap(clientId -> insertTestInvite(clientId, "delinv@test.com", "delinvuser",
							inviteCode))
					.then(userInviteService.deleteUserInvitation(inviteCode));

			StepVerifier.create(result)
					.assertNext(deleted -> assertThat(deleted).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("after deletion, getUserInvitation returns empty")
		void afterDeletion_GetReturnsEmpty() {

			String inviteCode = "del22345678901234567890123456789";

			Mono<UserInvite> result = insertTestClient("DLTWO", "Delete Client Two", "BUS")
					.flatMap(clientId -> insertTestInvite(clientId, "delinv2@test.com", "delinvusr2",
							inviteCode))
					.then(userInviteService.deleteUserInvitation(inviteCode))
					.then(userInviteService.getUserInvitation(inviteCode));

			StepVerifier.create(result)
					.verifyComplete();
		}

		@Test
		@DisplayName("non-existent invite code - should return false")
		void nonExistentCode_ReturnsFalse() {

			StepVerifier.create(userInviteService.deleteUserInvitation("nonexistent_code_for_delete_test"))
					.assertNext(deleted -> assertThat(deleted).isFalse())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getAllInvitedUsers()")
	class GetAllInvitedUsersTests {

		@Test
		@DisplayName("with invites for current client - should return paged results")
		void withInvites_ReturnsPagedResults() {

			Mono<Page<UserInvite>> result = insertTestClient("GALLON", "GetAll Client One", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestInvite(clientId, "page1@test.com", "pageuser1",
									"pag12345678901234567890123456789"))
							.then(insertTestInvite(clientId, "page2@test.com", "pageuser2",
									"pag22345678901234567890123456789"))
							.thenReturn(clientId))
					.flatMap(clientId -> {
						ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
								clientId, "GALLON",
								List.of("Authorities.User_READ", "Authorities.Logged_IN"));
						return userInviteService.getAllInvitedUsers(PageRequest.of(0, 10), null)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));
					});

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isEqualTo(2);
						assertThat(page.getContent()).hasSize(2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with no invites for current client - should return empty page")
		void noInvites_ReturnsEmptyPage() {

			Mono<Page<UserInvite>> result = insertTestClient("GALLTW", "GetAll Client Two", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> {
						ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
								clientId, "GALLTW",
								List.of("Authorities.User_READ", "Authorities.Logged_IN"));
						return userInviteService.getAllInvitedUsers(PageRequest.of(0, 10), null)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));
					});

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isZero();
						assertThat(page.getContent()).isEmpty();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("with filter condition - should return only matching invites")
		void withFilterCondition_ReturnsMatchingOnly() {

			Mono<Page<UserInvite>> result = insertTestClient("GALLTH", "GetAll Client Three", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestInvite(clientId, "filter1@test.com", "filtuser1",
									"fil12345678901234567890123456789"))
							.then(insertTestInvite(clientId, "filter2@test.com", "filtuser2",
									"fil22345678901234567890123456789"))
							.thenReturn(clientId))
					.flatMap(clientId -> {
						ContextAuthentication busAuth = TestDataFactory.createBusinessAuth(
								clientId, "GALLTH",
								List.of("Authorities.User_READ", "Authorities.Logged_IN"));
						FilterCondition condition = new FilterCondition();
						condition.setField("emailId");
						condition.setOperator(FilterConditionOperator.EQUALS);
						condition.setValue("filter1@test.com");
						return userInviteService.getAllInvitedUsers(PageRequest.of(0, 10), condition)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busAuth));
					});

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isEqualTo(1);
						assertThat(page.getContent()).hasSize(1);
						assertThat(page.getContent().get(0).getEmailId()).isEqualTo("filter1@test.com");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("invites from different client are not visible - should return only own client invites")
		void differentClient_NotVisible() {

			Mono<Page<UserInvite>> result = insertTestClient("GALFO", "GetAll Client Four", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestInvite(clientId, "otherclnt@test.com", "otherclnt",
									"oth12345678901234567890123456789"))
							.thenReturn(clientId))
					.then(insertTestClient("GALFI", "GetAll Client Five", "BUS"))
					.flatMap(otherClientId -> insertClientHierarchy(otherClientId, ULong.valueOf(1), null,
							null, null)
							.thenReturn(otherClientId))
					.flatMap(otherClientId -> {
						ContextAuthentication otherAuth = TestDataFactory.createBusinessAuth(
								otherClientId, "GALFI",
								List.of("Authorities.User_READ", "Authorities.Logged_IN"));
						return userInviteService.getAllInvitedUsers(PageRequest.of(0, 10), null)
								.contextWrite(ReactiveSecurityContextHolder.withAuthentication(otherAuth));
					});

			StepVerifier.create(result)
					.assertNext(page -> {
						assertThat(page).isNotNull();
						assertThat(page.getTotalElements()).isZero();
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("createInvite() - edge cases")
	class CreateInviteEdgeCaseTests {

		@Test
		@DisplayName("invite with phone number only (no email or username) - should create invite")
		void inviteWithPhoneOnly_Succeeds() {

			Mono<Map<String, Object>> result = insertTestClient("INVTEN", "Invite Client Ten", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> {
						UserInvite invite = new UserInvite();
						invite.setClientId(clientId);
						invite.setPhoneNumber("+1234567890");
						invite.setFirstName("Phone");
						invite.setLastName("Only");
						return userInviteService.createInvite(invite);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(resultMap -> {
						assertThat(resultMap).containsEntry("existingUser", Boolean.FALSE);
						UserInvite created = (UserInvite) resultMap.get("userRequest");
						assertThat(created.getPhoneNumber()).isEqualTo("+1234567890");
						assertThat(created.getInviteCode()).isNotNull().hasSize(32);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("invite with reportingTo null - should skip reporting validation")
		void inviteWithNullReportingTo_SkipsValidation() {

			Mono<Map<String, Object>> result = insertTestClient("INVELV", "Invite Client Eleven", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> {
						UserInvite invite = createInviteDto(clientId, "norpt@test.com", "norptuser",
								"NoReport", "User");
						invite.setReportingTo(null);
						return userInviteService.createInvite(invite);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(resultMap -> {
						assertThat(resultMap).containsEntry("existingUser", Boolean.FALSE);
						UserInvite created = (UserInvite) resultMap.get("userRequest");
						assertThat(created.getReportingTo()).isNull();
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("invite with non-existent reportingTo user ID - should fail with NOT_FOUND")
		void inviteWithNonExistentReportingTo_Fails() {

			Mono<Map<String, Object>> result = insertTestClient("INVTWL", "Invite Client Twelve", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> {
						UserInvite invite = createInviteDto(clientId, "badrpt@test.com", "badrptuser",
								"BadReport", "User");
						invite.setReportingTo(ULong.valueOf(999999));
						return userInviteService.createInvite(invite);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			// readById(reportingTo) throws NOT_FOUND for non-existent user
			StepVerifier.create(result)
					.expectErrorMatches(e -> e instanceof GenericException
							&& ((GenericException) e).getStatusCode() == org.springframework.http.HttpStatus.NOT_FOUND)
					.verify();
		}

		@Test
		@DisplayName("invite with null profileId - should skip profile access check")
		void inviteWithNullProfileId_SkipsProfileCheck() {

			Mono<Map<String, Object>> result = insertTestClient("INVTHT", "Invite Client Thirteen", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.thenReturn(clientId))
					.flatMap(clientId -> {
						UserInvite invite = createInviteDto(clientId, "noprof@test.com", "noprofuser",
								"NoProf", "User");
						invite.setProfileId(null);
						return userInviteService.createInvite(invite);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(resultMap -> {
						assertThat(resultMap).containsEntry("existingUser", Boolean.FALSE);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("UserInviteDAO direct operations")
	class UserInviteDAOTests {

		@Test
		@DisplayName("getUserInvitation retrieves correct invite by code")
		void getUserInvitation_ByCode_ReturnsCorrectInvite() {

			String code1 = "dao12345678901234567890123456789";
			String code2 = "dao22345678901234567890123456789";

			Mono<UserInvite> result = insertTestClient("DAONE", "DAO Client One", "BUS")
					.flatMap(clientId -> insertTestInvite(clientId, "dao1@test.com", "daouser1", code1)
							.then(insertTestInvite(clientId, "dao2@test.com", "daouser2", code2)))
					.then(userInviteService.getUserInvitation(code2));

			StepVerifier.create(result)
					.assertNext(invite -> {
						assertThat(invite.getInviteCode()).isEqualTo(code2);
						assertThat(invite.getEmailId()).isEqualTo("dao2@test.com");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("deleteUserInvitation with multiple invites - deletes only the one with matching code")
		void deleteUserInvitation_MultipleInvites_DeletesOnlyMatching() {

			String codeToDelete = "dld12345678901234567890123456789";
			String codeToKeep = "dld22345678901234567890123456789";

			Mono<UserInvite> result = insertTestClient("DATWO", "DAO Client Two", "BUS")
					.flatMap(clientId -> insertTestInvite(clientId, "daodel1@test.com", "daodelusr1",
							codeToDelete)
							.then(insertTestInvite(clientId, "daodel2@test.com", "daodelusr2", codeToKeep)))
					.then(userInviteService.deleteUserInvitation(codeToDelete))
					.then(userInviteService.getUserInvitation(codeToKeep));

			StepVerifier.create(result)
					.assertNext(invite -> {
						assertThat(invite.getInviteCode()).isEqualTo(codeToKeep);
						assertThat(invite.getEmailId()).isEqualTo("daodel2@test.com");
					})
					.verifyComplete();
		}
	}
}
