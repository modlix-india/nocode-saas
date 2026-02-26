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

import com.fincity.saas.commons.exeception.GenericException;
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
				.then(databaseClient.sql("DELETE FROM security_v2_role WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile_user WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_profile WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_past_passwords WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_past_pins WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user_token WHERE USER_ID > 1").then())
				.then(databaseClient.sql(
						"UPDATE security_user SET DESIGNATION_ID = NULL, REPORTING_TO = NULL WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_app_access WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_app WHERE CLIENT_ID > 1").then())
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

	@Nested
	@DisplayName("User Status Operations")
	class StatusOperationsTests {

		@Test
		@DisplayName("unblock locked user - should return true")
		void unblockUser_LockedUser_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("UBONE", "Unblock Client", "unblockuser",
					"unblockuser@test.com")
					.flatMap(userId -> {
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

	@Nested
	@DisplayName("Read Internal Operations")
	class ReadInternalTests {

		@Test
		@DisplayName("readInternal - should return user for valid ID")
		void readInternal_ValidUser_ReturnsUser() {
			Mono<User> result = createManagedClientAndUser("RIONE", "Read Int Client", "riuser1",
					"riuser1@test.com")
					.flatMap(userId -> userService.readInternal(userId));

			StepVerifier.create(result)
					.assertNext(user -> {
						assertThat(user).isNotNull();
						assertThat(user.getUserName()).isEqualTo("riuser1");
						assertThat(user.getEmailId()).isEqualTo("riuser1@test.com");
						assertThat(user.getFirstName()).isEqualTo("Test");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("readInternal - non-existent user returns empty")
		void readInternal_NonExistent_ReturnsEmpty() {
			Mono<User> result = userService.readInternal(ULong.valueOf(999999));

			StepVerifier.create(result)
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Read with Security Context")
	class ReadWithSecurityContextTests {

		@Test
		@DisplayName("read - system user can read any user")
		void read_SystemAuth_ReturnsUser() {
			Mono<User> result = createManagedClientAndUser("RDONE", "Read Client One", "rduser1",
					"rduser1@test.com")
					.flatMap(userId -> userService.read(userId))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(user -> {
						assertThat(user).isNotNull();
						assertThat(user.getUserName()).isEqualTo("rduser1");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("read - non-existent user ID returns forbidden error")
		void read_NonExistent_ReturnsForbidden() {
			Mono<User> result = userService.read(ULong.valueOf(999999))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable instanceof GenericException)
					.verify();
		}
	}

	@Nested
	@DisplayName("Check User Status")
	class CheckUserStatusTests {

		@Test
		@DisplayName("checkUserStatus - ACTIVE user with ACTIVE filter returns true")
		void checkUserStatus_ActiveUser_ReturnsTrue() {
			User user = new User();
			user.setStatusCode(SecurityUserStatusCode.ACTIVE);

			StepVerifier.create(userService.checkUserStatus(user, SecurityUserStatusCode.ACTIVE))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("checkUserStatus - LOCKED user with ACTIVE filter returns false")
		void checkUserStatus_LockedUser_ReturnsFalse() {
			User user = new User();
			user.setStatusCode(SecurityUserStatusCode.LOCKED);

			StepVerifier.create(userService.checkUserStatus(user, SecurityUserStatusCode.ACTIVE))
					.assertNext(result -> assertThat(result).isFalse())
					.verifyComplete();
		}

		@Test
		@DisplayName("checkUserStatus - ACTIVE user with multiple allowed statuses returns true")
		void checkUserStatus_MultipleStatuses_MatchesOne() {
			User user = new User();
			user.setStatusCode(SecurityUserStatusCode.ACTIVE);

			StepVerifier.create(userService.checkUserStatus(user,
					SecurityUserStatusCode.ACTIVE, SecurityUserStatusCode.INACTIVE))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("checkUserStatus - null user returns false")
		void checkUserStatus_NullUser_ReturnsFalse() {
			StepVerifier.create(userService.checkUserStatus(null, SecurityUserStatusCode.ACTIVE))
					.assertNext(result -> assertThat(result).isFalse())
					.verifyComplete();
		}

		@Test
		@DisplayName("checkUserStatus - empty status codes array returns false")
		void checkUserStatus_EmptyStatusCodes_ReturnsFalse() {
			User user = new User();
			user.setStatusCode(SecurityUserStatusCode.ACTIVE);

			StepVerifier.create(userService.checkUserStatus(user))
					.assertNext(result -> assertThat(result).isFalse())
					.verifyComplete();
		}

		@Test
		@DisplayName("checkUserStatus - DELETED user with DELETED filter returns true")
		void checkUserStatus_DeletedUser_ReturnsTrue() {
			User user = new User();
			user.setStatusCode(SecurityUserStatusCode.DELETED);

			StepVerifier.create(userService.checkUserStatus(user, SecurityUserStatusCode.DELETED))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("checkUserStatus - PASSWORD_EXPIRED user with PASSWORD_EXPIRED filter returns true")
		void checkUserStatus_PasswordExpired_ReturnsTrue() {
			User user = new User();
			user.setStatusCode(SecurityUserStatusCode.PASSWORD_EXPIRED);

			StepVerifier.create(userService.checkUserStatus(user, SecurityUserStatusCode.PASSWORD_EXPIRED))
					.assertNext(result -> assertThat(result).isTrue())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Lock and Unlock User Internal")
	class LockUnlockInternalTests {

		@Test
		@DisplayName("lockUserInternal - should set status to LOCKED")
		void lockUserInternal_SetsLockedStatus() {
			Mono<String> result = createManagedClientAndUser("LKONE", "Lock Client One", "lockuser1",
					"lockuser1@test.com")
					.flatMap(userId -> userService.lockUserInternal(userId,
							LocalDateTime.now().plusHours(2), "Too many failed attempts")
							.thenReturn(userId))
					.flatMap(userId -> databaseClient.sql(
							"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.map(row -> row.get("STATUS_CODE", String.class))
							.one());

			StepVerifier.create(result)
					.assertNext(status -> assertThat(status).isEqualTo("LOCKED"))
					.verifyComplete();
		}

		@Test
		@DisplayName("lockUserInternal - should set lock reason and expiry")
		void lockUserInternal_SetsLockDetails() {
			Mono<Map<String, Object>> result = createManagedClientAndUser("LKTWO", "Lock Client Two",
					"lockuser2", "lockuser2@test.com")
					.flatMap(userId -> userService.lockUserInternal(userId,
							LocalDateTime.now().plusHours(3), "Suspicious activity")
							.thenReturn(userId))
					.flatMap(userId -> databaseClient.sql(
							"SELECT STATUS_CODE, LOCKED_DUE_TO FROM security_user WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.map(row -> Map.<String, Object>of(
									"status", row.get("STATUS_CODE", String.class),
									"reason", row.get("LOCKED_DUE_TO", String.class)))
							.one());

			StepVerifier.create(result)
					.assertNext(row -> {
						assertThat(row.get("status")).isEqualTo("LOCKED");
						assertThat(row.get("reason")).isEqualTo("Suspicious activity");
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("unlockUserInternal - should set status to ACTIVE")
		void unlockUserInternal_SetsActiveStatus() {
			Mono<String> result = createManagedClientAndUser("ULTHR", "Unlock Client", "unlockint",
					"unlockint@test.com")
					.flatMap(userId -> userService.lockUserInternal(userId,
							LocalDateTime.now().plusHours(1), "Test lock")
							.then(userService.unlockUserInternal(userId))
							.thenReturn(userId))
					.flatMap(userId -> databaseClient.sql(
							"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.map(row -> row.get("STATUS_CODE", String.class))
							.one());

			StepVerifier.create(result)
					.assertNext(status -> assertThat(status).isEqualTo("ACTIVE"))
					.verifyComplete();
		}

		@Test
		@DisplayName("unlockUserInternal - should clear lock fields and reset counters")
		void unlockUserInternal_ClearsLockFields() {
			Mono<Map<String, Object>> result = createManagedClientAndUser("ULFOU", "Unlock Clear Client",
					"unlkclear", "unlkclear@test.com")
					.flatMap(userId -> userService.lockUserInternal(userId,
							LocalDateTime.now().plusHours(1), "Test lock")
							.then(userService.increaseFailedAttempt(userId, AuthenticationPasswordType.PASSWORD))
							.then(userService.unlockUserInternal(userId))
							.thenReturn(userId))
					.flatMap(userId -> databaseClient.sql(
							"SELECT NO_FAILED_ATTEMPT, LOCKED_DUE_TO FROM security_user WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.map(row -> {
								Short failedAttempt = row.get("NO_FAILED_ATTEMPT", Short.class);
								String lockReason = row.get("LOCKED_DUE_TO", String.class);
								return Map.<String, Object>of(
										"failedAttempt", failedAttempt != null ? failedAttempt : (short) 0,
										"lockReasonNull", lockReason == null);
							})
							.one());

			StepVerifier.create(result)
					.assertNext(row -> {
						assertThat(row.get("failedAttempt")).isEqualTo((short) 0);
						assertThat(row.get("lockReasonNull")).isEqualTo(true);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Make User Active / Inactive")
	class MakeUserActiveInactiveTests {

		@Test
		@DisplayName("makeUserActive - inactive user becomes ACTIVE")
		void makeUserActive_InactiveUser_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("MAONE", "MakeActive Client", "mauser1",
					"mauser1@test.com")
					.flatMap(userId -> databaseClient.sql(
							"UPDATE security_user SET STATUS_CODE = 'INACTIVE' WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.then()
							.thenReturn(userId))
					.flatMap(userId -> userService.makeUserActive(userId))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(active -> assertThat(active).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("makeUserActive - already active user returns true without update")
		void makeUserActive_AlreadyActive_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("MATWO", "MakeActive Client 2", "mauser2",
					"mauser2@test.com")
					.flatMap(userId -> userService.makeUserActive(userId))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(active -> assertThat(active).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("makeUserActive - status is ACTIVE in DB after activation")
		void makeUserActive_VerifyDBStatus() {
			Mono<String> result = createManagedClientAndUser("MATHR", "MakeActive Client 3", "mauser3",
					"mauser3@test.com")
					.flatMap(userId -> databaseClient.sql(
							"UPDATE security_user SET STATUS_CODE = 'INACTIVE' WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.then()
							.thenReturn(userId))
					.flatMap(userId -> userService.makeUserActive(userId).thenReturn(userId))
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

		@Test
		@DisplayName("makeUserInActive - active user becomes INACTIVE")
		void makeUserInActive_ActiveUser_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("MIONE", "MakeInactive Client", "miuser1",
					"miuser1@test.com")
					.flatMap(userId -> userService.makeUserInActive(userId))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(inactive -> assertThat(inactive).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("makeUserInActive - already inactive user returns true without update")
		void makeUserInActive_AlreadyInactive_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("MITWO", "MakeInactive Client 2", "miuser2",
					"miuser2@test.com")
					.flatMap(userId -> databaseClient.sql(
							"UPDATE security_user SET STATUS_CODE = 'INACTIVE' WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.then()
							.thenReturn(userId))
					.flatMap(userId -> userService.makeUserInActive(userId))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(inactive -> assertThat(inactive).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("makeUserInActive - status is INACTIVE in DB after deactivation")
		void makeUserInActive_VerifyDBStatus() {
			Mono<String> result = createManagedClientAndUser("MITHR", "MakeInactive Client 3", "miuser3",
					"miuser3@test.com")
					.flatMap(userId -> userService.makeUserInActive(userId).thenReturn(userId))
					.flatMap(userId -> databaseClient.sql(
							"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.map(row -> row.get("STATUS_CODE", String.class))
							.one())
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(status -> assertThat(status).isEqualTo("INACTIVE"))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Delete User (Soft Delete)")
	class DeleteUserTests {

		@Test
		@DisplayName("delete - should soft delete by setting status to DELETED")
		void delete_SetsDeletedStatus() {
			Mono<String> result = createManagedClientAndUser("DLONE", "Delete Client One", "deluser1",
					"deluser1@test.com")
					.flatMap(userId -> userService.delete(userId).thenReturn(userId))
					.flatMap(userId -> databaseClient.sql(
							"SELECT STATUS_CODE FROM security_user WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.map(row -> row.get("STATUS_CODE", String.class))
							.one())
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(status -> assertThat(status).isEqualTo("DELETED"))
					.verifyComplete();
		}

		@Test
		@DisplayName("delete - should return 1 on successful deletion")
		void delete_ReturnsOne() {
			Mono<Integer> result = createManagedClientAndUser("DLTWO", "Delete Client Two", "deluser2",
					"deluser2@test.com")
					.flatMap(userId -> userService.delete(userId))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(1))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("PIN Failed Attempt Operations")
	class PinFailedAttemptTests {

		@Test
		@DisplayName("increaseFailedAttempt PIN - should increase PIN counter")
		void increaseFailedAttempt_Pin_ReturnsCounter() {
			Mono<Short> result = createManagedClientAndUser("PFONE", "Pin Fail Client", "pinuser1",
					"pinuser1@test.com")
					.flatMap(userId -> userService.increaseFailedAttempt(userId,
							AuthenticationPasswordType.PIN));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isGreaterThanOrEqualTo((short) 1))
					.verifyComplete();
		}

		@Test
		@DisplayName("resetFailedAttempt PIN - should reset PIN counter")
		void resetFailedAttempt_Pin_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("PFTWO", "Pin Fail Client 2", "pinuser2",
					"pinuser2@test.com")
					.flatMap(userId -> userService.increaseFailedAttempt(userId,
							AuthenticationPasswordType.PIN)
							.then(userService.resetFailedAttempt(userId,
									AuthenticationPasswordType.PIN)));

			StepVerifier.create(result)
					.assertNext(reset -> assertThat(reset).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("increaseFailedAttempt PIN - multiple increases should accumulate")
		void increaseFailedAttempt_Pin_MultipleIncreases() {
			Mono<Short> result = createManagedClientAndUser("PFTHR", "Pin Fail Client 3", "pinuser3",
					"pinuser3@test.com")
					.flatMap(userId -> userService.increaseFailedAttempt(userId, AuthenticationPasswordType.PIN)
							.then(userService.increaseFailedAttempt(userId, AuthenticationPasswordType.PIN))
							.then(userService.increaseFailedAttempt(userId, AuthenticationPasswordType.PIN)));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isGreaterThanOrEqualTo((short) 3))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("OTP Failed Attempt Operations")
	class OtpFailedAttemptTests {

		@Test
		@DisplayName("increaseFailedAttempt OTP - should increase OTP counter")
		void increaseFailedAttempt_Otp_ReturnsCounter() {
			Mono<Short> result = createManagedClientAndUser("OFONE", "OTP Fail Client", "otpuser1",
					"otpuser1@test.com")
					.flatMap(userId -> userService.increaseFailedAttempt(userId,
							AuthenticationPasswordType.OTP));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isGreaterThanOrEqualTo((short) 1))
					.verifyComplete();
		}

		@Test
		@DisplayName("resetFailedAttempt OTP - should reset OTP counter")
		void resetFailedAttempt_Otp_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("OFTWO", "OTP Fail Client 2", "otpuser2",
					"otpuser2@test.com")
					.flatMap(userId -> userService.increaseFailedAttempt(userId,
							AuthenticationPasswordType.OTP)
							.then(userService.resetFailedAttempt(userId,
									AuthenticationPasswordType.OTP)));

			StepVerifier.create(result)
					.assertNext(reset -> assertThat(reset).isTrue())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Resend Attempt Operations")
	class ResendAttemptTests {

		@Test
		@DisplayName("increaseResendAttempt - should increase counter")
		void increaseResendAttempt_ReturnsCounter() {
			Mono<Short> result = createManagedClientAndUser("RSONE", "Resend Client", "rsnduser1",
					"rsnduser1@test.com")
					.flatMap(userId -> userService.increaseResendAttempt(userId));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isGreaterThanOrEqualTo((short) 1))
					.verifyComplete();
		}

		@Test
		@DisplayName("resetResendAttempt - should reset counter to zero")
		void resetResendAttempt_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("RSTWO", "Resend Client 2", "rsnduser2",
					"rsnduser2@test.com")
					.flatMap(userId -> userService.increaseResendAttempt(userId)
							.then(userService.increaseResendAttempt(userId))
							.then(userService.resetResendAttempt(userId)));

			StepVerifier.create(result)
					.assertNext(reset -> assertThat(reset).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("increaseResendAttempt - multiple increases should accumulate")
		void increaseResendAttempt_MultipleIncreases() {
			Mono<Short> result = createManagedClientAndUser("RSTHR", "Resend Client 3", "rsnduser3",
					"rsnduser3@test.com")
					.flatMap(userId -> userService.increaseResendAttempt(userId)
							.then(userService.increaseResendAttempt(userId)));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isGreaterThanOrEqualTo((short) 2))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getNonDeletedUserStatusCodes")
	class NonDeletedStatusCodesTests {

		@Test
		@DisplayName("getNonDeletedUserStatusCodes - should return 4 statuses excluding DELETED")
		void getNonDeletedStatusCodes_Returns4Statuses() {
			SecurityUserStatusCode[] codes = userService.getNonDeletedUserStatusCodes();

			assertThat(codes).hasSize(4);
			assertThat(codes).containsExactlyInAnyOrder(
					SecurityUserStatusCode.ACTIVE,
					SecurityUserStatusCode.INACTIVE,
					SecurityUserStatusCode.LOCKED,
					SecurityUserStatusCode.PASSWORD_EXPIRED);
			assertThat(codes).doesNotContain(SecurityUserStatusCode.DELETED);
		}
	}

	@Nested
	@DisplayName("Check If User Is Owner")
	class CheckIfUserIsOwnerTests {

		@Test
		@DisplayName("checkIfUserIsOwner - null userId returns empty")
		void checkIfUserIsOwner_NullUserId_ReturnsEmpty() {
			StepVerifier.create(userService.checkIfUserIsOwner(null))
					.verifyComplete();
		}

		@Test
		@DisplayName("checkIfUserIsOwner - user without owner role returns false")
		void checkIfUserIsOwner_NonOwner_ReturnsFalse() {
			Mono<Boolean> result = createManagedClientAndUser("OWONE", "Owner Client", "ownerchk1",
					"ownerchk1@test.com")
					.flatMap(userId -> userService.checkIfUserIsOwner(userId));

			StepVerifier.create(result)
					.assertNext(isOwner -> assertThat(isOwner).isFalse())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Check User Exists Across Apps")
	class CheckUserExistsAcrossAppsTests {

		@Test
		@DisplayName("checkUserExistsAcrossApps - all null params throws error")
		void checkUserExistsAcrossApps_AllNull_ThrowsError() {
			Mono<Boolean> result = userService.checkUserExistsAcrossApps(null, null, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable instanceof GenericException)
					.verify();
		}

		@Test
		@DisplayName("checkUserExistsAcrossApps - existing user returns true")
		void checkUserExistsAcrossApps_ExistingUser_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("CEONE", "CExist Client", "ceuser1",
					"ceuser1@test.com")
					.then(userService.checkUserExistsAcrossApps("ceuser1", null, null))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(exists -> assertThat(exists).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("checkUserExistsAcrossApps - non-existing user returns false")
		void checkUserExistsAcrossApps_NonExisting_ReturnsFalse() {
			Mono<Boolean> result = userService.checkUserExistsAcrossApps(
					"nonexistentuser_xyz_12345", null, null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(exists -> assertThat(exists).isFalse())
					.verifyComplete();
		}

		@Test
		@DisplayName("checkUserExistsAcrossApps - check by email")
		void checkUserExistsAcrossApps_ByEmail_ReturnsTrue() {
			Mono<Boolean> result = createManagedClientAndUser("CETWO", "CExist Client 2", "ceuser2",
					"ceuser2@test.com")
					.then(userService.checkUserExistsAcrossApps(null, "ceuser2@test.com", null))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(exists -> assertThat(exists).isTrue())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Role Assignment Operations")
	class RoleAssignmentTests {

		private Mono<ULong> insertTestRole(ULong clientId, String name) {
			return databaseClient.sql(
					"INSERT INTO security_v2_role (CLIENT_ID, NAME, SHORT_NAME, DESCRIPTION) VALUES (:clientId, :name, :shortName, :desc)")
					.bind("clientId", clientId.longValue())
					.bind("name", name)
					.bind("shortName", name.toUpperCase())
					.bind("desc", "Test role: " + name)
					.filter(s -> s.returnGeneratedValues("ID"))
					.map(row -> ULong.valueOf(row.get("ID", Long.class)))
					.one();
		}

		private Mono<Void> insertUserRole(ULong userId, ULong roleId) {
			return databaseClient.sql(
					"INSERT INTO security_v2_user_role (USER_ID, ROLE_ID) VALUES (:userId, :roleId)")
					.bind("userId", userId.longValue())
					.bind("roleId", roleId.longValue())
					.then();
		}

		@Test
		@DisplayName("assignRoleToUser - already assigned role returns true (idempotent check)")
		void assignRoleToUser_AlreadyAssigned_ReturnsTrue() {
			// When a role is already assigned, the service short-circuits and returns true
			Mono<Boolean> result = insertTestClient("ARONE", "Assign Role Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "aruser1", "aruser1@test.com", "pass123"),
									insertTestRole(clientId, "TestRole_AR1"))))
					.flatMap(tuple -> insertUserRole(tuple.getT1(), tuple.getT2())
							.then(userService.assignRoleToUser(tuple.getT1(), tuple.getT2())))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(assigned -> assertThat(assigned).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("assignRoleToUser - role not in any profile triggers forbidden error")
		void assignRoleToUser_RoleNotInProfile_Forbidden() {
			// When role is not accessible via profiles, the service returns forbidden
			Mono<Boolean> result = insertTestClient("ARTWO", "Assign Role Client 2", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "aruser2", "aruser2@test.com", "pass123"),
									insertTestRole(clientId, "TestRole_AR2_NoProfile"))))
					.flatMap(tuple -> userService.assignRoleToUser(tuple.getT1(), tuple.getT2()))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable instanceof GenericException)
					.verify();
		}

		@Test
		@DisplayName("removeRoleFromUser - should remove role assigned via direct SQL")
		void removeRoleFromUser_ExistingRole_ReturnsTrue() {
			Mono<Boolean> result = insertTestClient("ARTHR", "Remove Role Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "rruser1", "rruser1@test.com", "pass123"),
									insertTestRole(clientId, "TestRole_RR1"))))
					.flatMap(tuple -> insertUserRole(tuple.getT1(), tuple.getT2())
							.then(userService.removeRoleFromUser(tuple.getT1(), tuple.getT2())))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(removed -> assertThat(removed).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("removeRoleFromUser - removing non-assigned role returns false")
		void removeRoleFromUser_NonAssigned_ReturnsFalse() {
			Mono<Boolean> result = insertTestClient("ARFOU", "Remove Role Client 2", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "rruser2", "rruser2@test.com", "pass123"),
									insertTestRole(clientId, "TestRole_RR2"))))
					.flatMap(tuple -> userService.removeRoleFromUser(tuple.getT1(), tuple.getT2()))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(removed -> assertThat(removed).isFalse())
					.verifyComplete();
		}

		@Test
		@DisplayName("removeRoleFromUser - removal clears DB entry")
		void removeRoleFromUser_VerifyDBRemoval() {
			Mono<Integer> result = insertTestClient("ARFIV", "Remove Role DB Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "rruser3", "rruser3@test.com", "pass123"),
									insertTestRole(clientId, "TestRole_RR3"))))
					.flatMap(tuple -> insertUserRole(tuple.getT1(), tuple.getT2())
							.then(userService.removeRoleFromUser(tuple.getT1(), tuple.getT2()))
							.thenReturn(tuple))
					.flatMap(tuple -> databaseClient.sql(
							"SELECT COUNT(*) as cnt FROM security_v2_user_role WHERE USER_ID = :userId AND ROLE_ID = :roleId")
							.bind("userId", tuple.getT1().longValue())
							.bind("roleId", tuple.getT2().longValue())
							.map(row -> row.get("cnt", Integer.class))
							.one())
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(0))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Profile Assignment Operations")
	class ProfileAssignmentTests {

		private Mono<ULong[]> insertTestAppAndProfile(ULong clientId, String appCode, String profileName) {
			return insertTestApp(clientId, appCode, "Test App " + appCode)
					.flatMap(appId -> databaseClient.sql(
							"INSERT INTO security_profile (CLIENT_ID, NAME, APP_ID) VALUES (:clientId, :name, :appId)")
							.bind("clientId", clientId.longValue())
							.bind("name", profileName)
							.bind("appId", appId.longValue())
							.filter(s -> s.returnGeneratedValues("ID"))
							.map(row -> ULong.valueOf(row.get("ID", Long.class)))
							.one()
							.map(profileId -> new ULong[] { appId, profileId }));
		}

		@Test
		@DisplayName("assignProfileToUser - should assign profile successfully")
		void assignProfileToUser_ValidProfile_ReturnsTrue() {
			Mono<Boolean> result = insertTestClient("APONE", "Assign Profile Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "apuser1", "apuser1@test.com", "pass123"),
									insertTestAppAndProfile(clientId, "APAPP1", "TestProfile_AP1"))))
					.flatMap(tuple -> userService.assignProfileToUser(tuple.getT1(), tuple.getT2()[1]))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(assigned -> assertThat(assigned).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("removeProfileFromUser - should remove assigned profile")
		void removeProfileFromUser_ExistingProfile_ReturnsTrue() {
			Mono<Boolean> result = insertTestClient("APTWO", "Remove Profile Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "rpuser1", "rpuser1@test.com", "pass123"),
									insertTestAppAndProfile(clientId, "RPAPP1", "TestProfile_RP1"))))
					.flatMap(tuple -> userService.assignProfileToUser(tuple.getT1(), tuple.getT2()[1])
							.then(userService.removeProfileFromUser(tuple.getT1(), tuple.getT2()[1])))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(removed -> assertThat(removed).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("assignProfileToUser - profile assignment persists in DB")
		void assignProfileToUser_VerifyDBPersistence() {
			Mono<Integer> result = insertTestClient("APTHR", "Assign Profile DB Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "apuser3", "apuser3@test.com", "pass123"),
									insertTestAppAndProfile(clientId, "APAPP3", "TestProfile_AP3"))))
					.flatMap(tuple -> {
						ULong userId = tuple.getT1();
						ULong profileId = tuple.getT2()[1];
						return userService.assignProfileToUser(userId, profileId)
								.thenReturn(new ULong[] { userId, profileId });
					})
					.flatMap(ids -> databaseClient.sql(
							"SELECT COUNT(*) as cnt FROM security_profile_user WHERE USER_ID = :userId AND PROFILE_ID = :profileId")
							.bind("userId", ids[0].longValue())
							.bind("profileId", ids[1].longValue())
							.map(row -> row.get("cnt", Integer.class))
							.one())
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(1))
					.verifyComplete();
		}

		@Test
		@DisplayName("removeProfileFromUser - removal clears DB entry")
		void removeProfileFromUser_VerifyDBRemoval() {
			Mono<Integer> result = insertTestClient("APFOU", "Remove Profile DB Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertClientManager(clientId, ULong.valueOf(1)))
							.then(Mono.zip(
									insertTestUser(clientId, "rpuser2", "rpuser2@test.com", "pass123"),
									insertTestAppAndProfile(clientId, "RPAPP2", "TestProfile_RP2"))))
					.flatMap(tuple -> {
						ULong userId = tuple.getT1();
						ULong profileId = tuple.getT2()[1];
						return userService.assignProfileToUser(userId, profileId)
								.then(userService.removeProfileFromUser(userId, profileId))
								.thenReturn(new ULong[] { userId, profileId });
					})
					.flatMap(ids -> databaseClient.sql(
							"SELECT COUNT(*) as cnt FROM security_profile_user WHERE USER_ID = :userId AND PROFILE_ID = :profileId")
							.bind("userId", ids[0].longValue())
							.bind("profileId", ids[1].longValue())
							.map(row -> row.get("cnt", Integer.class))
							.one())
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(count -> assertThat(count).isEqualTo(0))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("GetSoxObjectName")
	class GetSoxObjectNameTests {

		@Test
		@DisplayName("getSoxObjectName - should return USER")
		void getSoxObjectName_ReturnsUser() {
			assertThat(userService.getSoxObjectName().getLiteral()).isEqualTo("USER");
		}
	}

	@Nested
	@DisplayName("Emails of Deleted Users")
	class EmailsDeletedUsersTests {

		@Test
		@DisplayName("getEmailsOfUsers - should not return emails of deleted users")
		void getEmails_DeletedUser_ExcludesDeleted() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<List<String>> result = insertTestClient("EDONE", "EmailDel Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestUser(clientId, "eduser1_" + ts,
									"eduser1_" + ts + "@test.com", "password123"))
							.flatMap(userId1 -> insertTestUser(clientId, "eduser2_" + ts,
									"eduser2_" + ts + "@test.com", "password123")
									.flatMap(userId2 -> databaseClient.sql(
											"UPDATE security_user SET STATUS_CODE = 'DELETED' WHERE ID = :userId")
											.bind("userId", userId2.longValue())
											.then()
											.thenReturn(List.of(userId1, userId2)))))
					.flatMap(userIds -> userService.getEmailsOfUsers(userIds));

			StepVerifier.create(result)
					.assertNext(emails -> {
						assertThat(emails).hasSize(1);
						assertThat(emails.get(0)).contains("eduser1_" + ts);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Combined Lock-Unlock-Active Workflows")
	class CombinedWorkflowTests {

		@Test
		@DisplayName("lock then makeUserActive should set status to ACTIVE")
		void lockThenMakeActive_StatusBecomesActive() {
			Mono<Boolean> result = createManagedClientAndUser("CWONE", "Combo Client One", "cwuser1",
					"cwuser1@test.com")
					.flatMap(userId -> userService.lockUserInternal(userId,
							LocalDateTime.now().plusHours(1), "Test")
							.then(userService.makeUserActive(userId)))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(active -> assertThat(active).isTrue())
					.verifyComplete();
		}

		@Test
		@DisplayName("makeInActive then makeActive round-trip works")
		void inactiveThenActive_RoundTrip() {
			Mono<String> result = createManagedClientAndUser("CWTWO", "Combo Client Two", "cwuser2",
					"cwuser2@test.com")
					.flatMap(userId -> userService.makeUserInActive(userId)
							.then(userService.makeUserActive(userId))
							.thenReturn(userId))
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

		@Test
		@DisplayName("increaseFailedAttempt across all types should track separately")
		void increaseFailedAttempt_AllTypes_TrackedSeparately() {
			Mono<Map<String, Short>> result = createManagedClientAndUser("CWTHR", "Combo Client Three",
					"cwuser3", "cwuser3@test.com")
					.flatMap(userId -> userService.increaseFailedAttempt(userId, AuthenticationPasswordType.PASSWORD)
							.then(userService.increaseFailedAttempt(userId, AuthenticationPasswordType.PASSWORD))
							.then(userService.increaseFailedAttempt(userId, AuthenticationPasswordType.PIN))
							.then(userService.increaseFailedAttempt(userId, AuthenticationPasswordType.OTP))
							.then(userService.increaseFailedAttempt(userId, AuthenticationPasswordType.OTP))
							.then(userService.increaseFailedAttempt(userId, AuthenticationPasswordType.OTP))
							.thenReturn(userId))
					.flatMap(userId -> databaseClient.sql(
							"SELECT NO_FAILED_ATTEMPT, NO_PIN_FAILED_ATTEMPT, NO_OTP_FAILED_ATTEMPT FROM security_user WHERE ID = :userId")
							.bind("userId", userId.longValue())
							.map(row -> Map.of(
									"password", row.get("NO_FAILED_ATTEMPT", Short.class),
									"pin", row.get("NO_PIN_FAILED_ATTEMPT", Short.class),
									"otp", row.get("NO_OTP_FAILED_ATTEMPT", Short.class)))
							.one());

			StepVerifier.create(result)
					.assertNext(counts -> {
						assertThat(counts.get("password")).isEqualTo((short) 2);
						assertThat(counts.get("pin")).isEqualTo((short) 1);
						assertThat(counts.get("otp")).isEqualTo((short) 3);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("User Read By IDs")
	class ReadByIdsTests {

		@Test
		@DisplayName("readByIds - should return users for multiple IDs")
		void readByIds_MultipleUsers_ReturnsList() {
			String ts = String.valueOf(System.currentTimeMillis());

			Mono<List<User>> result = insertTestClient("RBONE", "ReadByIds Client", "BUS")
					.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
							.then(insertTestUser(clientId, "rbuser1_" + ts,
									"rbuser1_" + ts + "@test.com", "pass123"))
							.flatMap(userId1 -> insertTestUser(clientId, "rbuser2_" + ts,
									"rbuser2_" + ts + "@test.com", "pass123")
									.map(userId2 -> List.of(userId1, userId2))))
					.flatMap(userIds -> userService.readByIds(userIds, null))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(users -> {
						assertThat(users).isNotNull();
						assertThat(users).hasSize(2);
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("readByIds - empty list returns empty")
		void readByIds_EmptyList_ReturnsEmpty() {
			Mono<List<User>> result = userService.readByIds(List.of(), null)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.assertNext(users -> assertThat(users).isEmpty())
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Delete with Client Access Verification")
	class DeleteAccessTests {

		@Test
		@DisplayName("delete - business auth without management should fail")
		void delete_WrongClient_Forbidden() {
			Mono<Integer> result = createManagedClientAndUser("DAONE", "DelAccess Client", "dauser1",
					"dauser1@test.com")
					.flatMap(userId -> insertTestClient("DATWO", "DelAccess Client 2", "BUS")
							.flatMap(bus2Id -> insertClientHierarchy(bus2Id, ULong.valueOf(1), null, null, null)
									.thenReturn(bus2Id))
							.flatMap(bus2Id -> {
								ContextAuthentication bus2Auth = TestDataFactory.createBusinessAuth(
										bus2Id, "DATWO",
										List.of("Authorities.User_READ", "Authorities.User_DELETE",
												"Authorities.User_UPDATE", "Authorities.Logged_IN"));
								return userService.delete(userId)
										.contextWrite(
												ReactiveSecurityContextHolder.withAuthentication(bus2Auth));
							}));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable instanceof GenericException)
					.verify();
		}
	}

	@Nested
	@DisplayName("Make User Active/Inactive Access Control")
	class ActiveInactiveAccessControlTests {

		@Test
		@DisplayName("makeUserActive - business auth for unmanaged client should fail")
		void makeUserActive_UnmanagedClient_Forbidden() {
			Mono<Boolean> result = createManagedClientAndUser("AAONE", "ActiveAccess Client", "aauser1",
					"aauser1@test.com")
					.flatMap(userId -> insertTestClient("AATWO", "ActiveAccess Client 2", "BUS")
							.flatMap(bus2Id -> insertClientHierarchy(bus2Id, ULong.valueOf(1), null, null, null)
									.thenReturn(bus2Id))
							.flatMap(bus2Id -> {
								ContextAuthentication bus2Auth = TestDataFactory.createBusinessAuth(
										bus2Id, "AATWO",
										List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
								return userService.makeUserActive(userId)
										.contextWrite(
												ReactiveSecurityContextHolder.withAuthentication(bus2Auth));
							}));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable instanceof GenericException)
					.verify();
		}

		@Test
		@DisplayName("makeUserInActive - business auth for unmanaged client should fail")
		void makeUserInActive_UnmanagedClient_Forbidden() {
			Mono<Boolean> result = createManagedClientAndUser("AIONE", "InactiveAccess Client", "aiuser1",
					"aiuser1@test.com")
					.flatMap(userId -> insertTestClient("AITWO", "InactiveAccess Client 2", "BUS")
							.flatMap(bus2Id -> insertClientHierarchy(bus2Id, ULong.valueOf(1), null, null, null)
									.thenReturn(bus2Id))
							.flatMap(bus2Id -> {
								ContextAuthentication bus2Auth = TestDataFactory.createBusinessAuth(
										bus2Id, "AITWO",
										List.of("Authorities.User_UPDATE", "Authorities.Logged_IN"));
								return userService.makeUserInActive(userId)
										.contextWrite(
												ReactiveSecurityContextHolder.withAuthentication(bus2Auth));
							}));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable instanceof GenericException)
					.verify();
		}
	}

	@Nested
	@DisplayName("Update Password with Security Context")
	class UpdatePasswordWithContextTests {

		@Test
		@DisplayName("updatePassword - missing new password should fail")
		void updatePassword_MissingNewPassword_Fails() {
			RequestUpdatePassword reqPassword = new RequestUpdatePassword();
			reqPassword.setOldPassword("oldpass");
			reqPassword.setNewPassword("");
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);

			Mono<Boolean> result = createManagedClientAndUser("UPONE", "UpdPwd Client", "upuser1",
					"upuser1@test.com")
					.flatMap(userId -> userService.updatePassword(userId, reqPassword))
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable instanceof GenericException)
					.verify();
		}

		@Test
		@DisplayName("updatePassword overload without userId - missing new password should fail")
		void updatePassword_NoUserId_MissingNewPassword_Fails() {
			RequestUpdatePassword reqPassword = new RequestUpdatePassword();
			reqPassword.setOldPassword("oldpass");
			reqPassword.setNewPassword("");
			reqPassword.setPassType(AuthenticationPasswordType.PASSWORD);

			Mono<Boolean> result = userService.updatePassword(reqPassword)
					.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

			StepVerifier.create(result)
					.expectErrorMatches(throwable -> throwable instanceof GenericException)
					.verify();
		}
	}
}
