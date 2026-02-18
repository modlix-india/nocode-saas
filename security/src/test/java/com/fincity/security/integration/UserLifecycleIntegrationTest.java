package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.service.UserService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserLifecycleIntegrationTest extends AbstractIntegrationTest {

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
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_past_passwords WHERE USER_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	@Test
	void createUser_WithValidData_ReturnsCreatedUser() {

		Mono<User> result = insertTestClient("BUSONE", "Business One", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(Mono.just(clientId)))
				.flatMap(clientId -> {
					User user = new User();
					user.setClientId(clientId);
					user.setUserName("newuser");
					user.setEmailId("newuser@test.com");
					user.setPhoneNumber("+11234567890");
					user.setFirstName("New");
					user.setLastName("User");
					user.setStatusCode(SecurityUserStatusCode.ACTIVE);
					return userService.create(user);
				})
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(createdUser -> {
					assertThat(createdUser).isNotNull();
					assertThat(createdUser.getId()).isNotNull();
					assertThat(createdUser.getUserName()).isEqualTo("newuser");
					assertThat(createdUser.getEmailId()).isEqualTo("newuser@test.com");
					assertThat(createdUser.getFirstName()).isEqualTo("New");
					assertThat(createdUser.getLastName()).isEqualTo("User");
					assertThat(createdUser.getStatusCode()).isEqualTo(SecurityUserStatusCode.ACTIVE);
				})
				.verifyComplete();
	}

	@Test
	void userStatusLifecycle_Active_Inactive_Active() {

		Mono<Boolean> lifecycle = insertTestClient("BUSTWO", "Business Two", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertTestUser(clientId, "statususer", "statususer@test.com", "fincity@123")))
				.flatMap(userId ->
						// Make user inactive
						userService.makeUserInActive(userId)
								.flatMap(inactiveResult -> {
									assertThat(inactiveResult).isTrue();
									// Verify the user is now INACTIVE by reading internally via SQL
									return databaseClient.sql(
													"SELECT STATUS_CODE FROM security_user WHERE ID = :id")
											.bind("id", userId.longValue())
											.map(row -> row.get("STATUS_CODE", String.class))
											.one();
								})
								.flatMap(status -> {
									assertThat(status).isEqualTo("INACTIVE");
									// Make user active again
									return userService.makeUserActive(userId);
								})
								.flatMap(activeResult -> {
									assertThat(activeResult).isTrue();
									// Verify the user is now ACTIVE
									return databaseClient.sql(
													"SELECT STATUS_CODE FROM security_user WHERE ID = :id")
											.bind("id", userId.longValue())
											.map(row -> row.get("STATUS_CODE", String.class))
											.one();
								})
								.map(status -> {
									assertThat(status).isEqualTo("ACTIVE");
									return true;
								})
				)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(lifecycle)
				.expectNext(true)
				.verifyComplete();
	}

	@Test
	void deleteUser_SoftDeletes_StatusBecomesDeleted() {

		// The delete flow requires canAccessClientForUserOperation which checks both
		// hierarchy (SYSTEM manages the client) and client_manager (user is a manager).
		// We insert a client_manager entry for sysadmin (ID=1) to satisfy this check.
		Mono<String> result = insertTestClient("BUSTHR", "Business Three", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertClientManager(clientId, ULong.valueOf(1)))
						.then(insertTestUser(clientId, "deleteuser", "deleteuser@test.com", "fincity@123")))
				.flatMap(userId -> userService.delete(userId)
						.flatMap(deleteCount -> {
							assertThat(deleteCount).isEqualTo(1);
							// Read the status directly from database to verify soft delete
							return databaseClient.sql(
											"SELECT STATUS_CODE FROM security_user WHERE ID = :id")
									.bind("id", userId.longValue())
									.map(row -> row.get("STATUS_CODE", String.class))
									.one();
						}))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(status -> assertThat(status).isEqualTo("DELETED"))
				.verifyComplete();
	}

	@Test
	void readUser_ExistingUser_ReturnsUserDetails() {

		Mono<User> result = insertTestClient("BUSFOR", "Business Four", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertTestUser(clientId, "readuser", "readuser@test.com", "fincity@123"))
						.flatMap(userId -> userService.read(userId)))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(user -> {
					assertThat(user).isNotNull();
					assertThat(user.getId()).isNotNull();
					assertThat(user.getUserName()).isEqualTo("readuser");
					assertThat(user.getEmailId()).isEqualTo("readuser@test.com");
					assertThat(user.getFirstName()).isEqualTo("Test");
					assertThat(user.getLastName()).isEqualTo("User");
					assertThat(user.getStatusCode()).isEqualTo(SecurityUserStatusCode.ACTIVE);
				})
				.verifyComplete();
	}

	@Test
	void checkUserExistsAcrossApps_ExistingEmail_ReturnsTrue() {

		Mono<Boolean> result = insertTestClient("BUSFIV", "Business Five", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertTestUser(clientId, "existsuser", "existsuser@test.com", "fincity@123")))
				.flatMap(userId -> userService.checkUserExistsAcrossApps(null, "existsuser@test.com", null))
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth));

		StepVerifier.create(result)
				.assertNext(exists -> assertThat(exists).isTrue())
				.verifyComplete();
	}

	@Test
	void multiTenantIsolation_CannotDeleteUserFromOtherClient() {

		// Create two independent business clients (BUS1 and BUS2), each managed by SYSTEM.
		// Create a user in BUS1. Then switch the security context to a BUS2 admin
		// (who does NOT manage BUS1) and attempt to delete the BUS1 user. The service
		// should deny access because BUS2 has no management relationship over BUS1.
		// Note: read() only checks authority, not client hierarchy. Write operations
		// (update/delete) enforce client isolation via canAccessClientForUserOperation.

		Mono<Integer> isolationTest = insertTestClient("BUSSIX", "Business Six", "BUS")
				.flatMap(bus1Id -> insertClientHierarchy(bus1Id, ULong.valueOf(1), null, null, null)
						.then(insertTestUser(bus1Id, "bus1user", "bus1user@test.com", "fincity@123"))
						.flatMap(bus1UserId -> insertTestClient("BUSSEV", "Business Seven", "BUS")
								.flatMap(bus2Id -> insertClientHierarchy(bus2Id, ULong.valueOf(1), null, null, null)
										.then(insertTestUser(bus2Id, "bus2admin", "bus2admin@test.com", "fincity@123"))
										.flatMap(bus2UserId -> {
											// Try to delete the BUS1 user while authenticated as BUS2
											ContextAuthentication bus2Auth = TestDataFactory.createBusinessAuth(
													bus2Id, "BUSSEV",
													List.of("Authorities.User_READ",
															"Authorities.User_DELETE",
															"Authorities.User_UPDATE",
															"Authorities.Logged_IN"));

											return userService.delete(bus1UserId)
													.contextWrite(ReactiveSecurityContextHolder
															.withAuthentication(bus2Auth));
										}))));

		// The delete should fail because BUS2 does not manage BUS1.
		StepVerifier.create(isolationTest)
				.expectErrorMatches(throwable ->
						throwable.getMessage() != null &&
								throwable.getMessage().toLowerCase().contains("cannot update"))
				.verify();
	}
}