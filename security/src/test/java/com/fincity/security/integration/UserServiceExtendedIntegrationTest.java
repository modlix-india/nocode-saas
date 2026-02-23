package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.RequestUpdatePassword;
import com.fincity.security.service.UserService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserServiceExtendedIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private UserService userService;

	private ContextAuthentication systemAuth;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_v2_user_role WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_past_passwords WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user_token WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> createManagedClientAndUser(String clientCode, String clientName, String userName,
			String email) {
		return insertTestClient(clientCode, clientName, "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestUser(clientId, userName, email, "fincity@123")));
	}

	private Mono<ULong[]> createManagedClientAndUserWithClientId(String clientCode, String clientName,
			String userName, String email) {
		return insertTestClient(clientCode, clientName, "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestUser(clientId, userName, email, "fincity@123"))
						.map(userId -> new ULong[] { clientId, userId }));
	}

	@Nested
	@DisplayName("User Update Operations")
	class UpdateTests {

		@Test
		@DisplayName("partial update via Map - should update firstName")
		void partialUpdate_ChangeName_ReturnsUpdated() {
			Mono<User> result = createManagedClientAndUser("UPDONE", "Update Client One", "upduser1",
					"upduser1@test.com")
					.flatMap(userId -> userService.update(userId, Map.of("firstName", "UpdatedFirst")))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(user -> {
						assertThat(user).isNotNull();
						assertThat(user.getFirstName()).isEqualTo("UpdatedFirst");
						assertThat(user.getUserName()).isEqualTo("upduser1");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("partial update via Map - should update multiple fields")
		void partialUpdate_MultipleFields_AllUpdated() {
			Mono<User> result = createManagedClientAndUser("UPDTWO", "Update Client Two", "upduser2",
					"upduser2@test.com")
					.flatMap(userId -> userService.update(userId,
							Map.of("firstName", "NewFirst", "lastName", "NewLast", "localeCode", "fr")))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(user -> {
						assertThat(user.getFirstName()).isEqualTo("NewFirst");
						assertThat(user.getLastName()).isEqualTo("NewLast");
						assertThat(user.getLocaleCode()).isEqualTo("fr");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("full entity update - should update user fields")
		void fullEntityUpdate_ReturnsUpdated() {
			Mono<User> result = createManagedClientAndUserWithClientId("UPDTHR", "Update Client Three",
					"upduser3", "upduser3@test.com")
					.flatMap(ids -> {
						ULong clientId = ids[0];
						ULong userId = ids[1];
						User updateUser = new User();
						updateUser.setId(userId);
						updateUser.setClientId(clientId);
						updateUser.setUserName("upduser3");
						updateUser.setEmailId("upduser3@test.com");
						updateUser.setFirstName("EntityFirst");
						updateUser.setLastName("EntityLast");
						updateUser.setMiddleName("M");
						updateUser.setStatusCode(SecurityUserStatusCode.ACTIVE);
						return userService.update(updateUser);
					})
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(user -> {
						assertThat(user.getFirstName()).isEqualTo("EntityFirst");
						assertThat(user.getLastName()).isEqualTo("EntityLast");
						assertThat(user.getMiddleName()).isEqualTo("M");
					})
					.verifyComplete();
		}
	}

	// Note: Role and Profile assignment via UserService are tested at the DAO level
	// in UserDAOIntegrationTest (addRoleToUser, removeRoleForUser, addProfileToUser,
	// removeProfileForUser). Service-level tests for these operations require complex
	// cross-service auth chain state (hasAccessToRoles, hasAccessToProfiles) that is
	// sensitive to test execution order with shared Testcontainers DB.

	@Nested
	@DisplayName("User Status Operations")
	class StatusOperationsTests {

		@Test
		@DisplayName("unblock locked user - should return true")
		void unblockUser_LockedUser_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("UBONE", "Unblock Client", "unblockuser",
					"unblockuser@test.com")
					.flatMap(userId -> {
						// Lock the user first via direct SQL
						return databaseClient.sql(
								"UPDATE security_user SET STATUS_CODE = 'LOCKED', LOCKED_UNTIL = :lockUntil, LOCKED_DUE_TO = 'Test' WHERE ID = :userId")
								.bind("lockUntil", LocalDateTime.now().plusHours(1))
								.bind("userId", userId.longValue())
								.then()
								.thenReturn(userId);
					})
					.flatMap(userId -> userService.unblockUser(userId))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(unblocked -> assertThat(unblocked).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("unblock locked user - status should be ACTIVE in DB")
		void unblockUser_StatusBecomesActive() {
			Mono<String> result = createManagedClientAndUser("UBTWO", "Unblock Client 2", "unblock2",
					"unblock2@test.com")
					.flatMap(userId -> databaseClient.sql(
							"UPDATE security_user SET STATUS_CODE = 'LOCKED', LOCKED_UNTIL = :lockUntil, LOCKED_DUE_TO = 'Test' WHERE ID = :userId")
							.bind("lockUntil", LocalDateTime.now().plusHours(1))
							.bind("userId", userId.longValue())
							.then()
							.thenReturn(userId))
					.flatMap(userId -> userService.unblockUser(userId).thenReturn(userId))
					.flatMap(userId -> databaseClient.sql(
							"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.map(row -> row.get("STATUS_CODE", String.class))
							.one())
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(status -> assertThat(status).isEqualTo("ACTIVE"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Password Update Operations")
	class PasswordUpdateTests {

		@Test
		@DisplayName("updatePassword without authentication - should fail with forbidden")
		void updatePassword_NoAuth_Fails() {
			// updatePassword requires an authenticated context - test the guard
			RequestUpdatePassword reqPassword = new RequestUpdatePassword();
			reqPassword.setOldPassword("old");
			reqPassword.setNewPassword("new");
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);

			StepVerifier.create(userService.updatePassword(ULong.valueOf(999), reqPassword))
					.expectError()
					.verify();
		}

		@Test
		@DisplayName("increaseFailedAttempt through service - should increase counter")
		void increaseFailedAttempt_ReturnsCounter() {
			String ts = String.valueOf(System.currentTimeMillis());
			Mono<Short> result = insertTestUser(ULong.valueOf(1), "failpwd_" + ts,
					"failpwd_" + ts + "@test.com", "password123")
					.flatMap(userId -> userService.increaseFailedAttempt(userId,
							AuthenticationPasswordType.PASSWORD));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isGreaterThanOrEqualTo((short) 1))
					.verifyComplete();
		}

		@Test
		@DisplayName("resetFailedAttempt through service - should reset to zero")
		void resetFailedAttempt_ReturnsTrue() {
			String ts = String.valueOf(System.currentTimeMillis());
			Mono<Boolean> result = insertTestUser(ULong.valueOf(1), "resetpwd_" + ts,
					"resetpwd_" + ts + "@test.com", "password123")
					.flatMap(userId -> userService.increaseFailedAttempt(userId,
							AuthenticationPasswordType.PASSWORD)
							.then(userService.resetFailedAttempt(userId,
									AuthenticationPasswordType.PASSWORD)));

			StepVerifier.create(result)
					.assertNext(reset -> assertThat(reset).isTrue())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Email Retrieval Operations")
	class EmailRetrievalTests {

		@Test
		@DisplayName("getEmailsOfUsers - should return emails for valid user IDs")
		void getEmails_MultipleUsers_ReturnsEmails() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<List<String>> result = insertTestClient("EMONE", "Email Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestUser(clientId, "emailuser1_" + ts,
									"emailuser1_" + ts + "@test.com", "password123"))
							.flatMap(userId1 -> insertTestUser(clientId, "emailuser2_" + ts,
									"emailuser2_" + ts + "@test.com", "password123")
									.map(userId2 -> List.of(userId1, userId2))))
					.flatMap(userIds -> userService.getEmailsOfUsers(userIds));

			StepVerifier.create(result)
					.assertNext(emails -> {
						assertThat(emails).isNotNull();
						assertThat(emails).hasSize(2);
						assertThat(emails).contains("emailuser1_" + ts + "@test.com",
								"emailuser2_" + ts + "@test.com");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("getEmailsOfUsers - empty list returns empty")
		void getEmails_EmptyList_ReturnsEmpty() {
			Mono<List<String>> result = userService.getEmailsOfUsers(List.of());

			StepVerifier.create(result)
					.assertNext(emails -> assertThat(emails).isEmpty())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Multi-tenant User Operations")
	class MultiTenantTests {

		@Test
		@DisplayName("update user from different client - should fail with forbidden")
		void updateUser_WrongClient_Forbidden() {
			// Create user in client A, try to update from client B context
			Mono<User> result = createManagedClientAndUser("MTONE", "Multi Client One", "mtuser1",
					"mtuser1@test.com")
					.flatMap(userId -> insertTestClient("MTTWO", "Multi Client Two", "BUS")
							.flatMap(bus2Id -> insertClientHierarchy(bus2Id, ULong.valueOf(1), null, null, null)
									.thenReturn(bus2Id))
							.flatMap(bus2Id -> {
								ContextAuthentication bus2Auth = TestDataFactory.createBusinessAuth(
										bus2Id, "MTTWO",
										List.of("Authorities.User_READ", "Authorities.User_UPDATE",
												"Authorities.Logged_IN"));
								return userService.update(userId, Map.of("firstName", "Hacked"))
										.contextWrite(ReactiveSecurityContextHolder.withAuthentication(bus2Auth));
							}));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable.getMessage() != null
							&& (throwable.getMessage().toLowerCase().contains("forbidden")
									|| throwable.getMessage().toLowerCase().contains("cannot update")))
					.verify();
		}
	}
}
