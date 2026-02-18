package com.fincity.security.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.UserService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserLifecycleIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private UserService userService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private ClientHierarchyService clientHierarchyService;

	@Autowired
	private AuthenticationService authenticationService;

	private MockedStatic<SecurityContextUtil> securityContextMock;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		securityContextMock = Mockito.mockStatic(SecurityContextUtil.class);
		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		securityContextMock.when(SecurityContextUtil::getUsersContextAuthentication)
				.thenReturn(Mono.just(ca));
		securityContextMock.when(SecurityContextUtil::getUsersContextUser)
				.thenReturn(Mono.just(ca.getUser()));
		securityContextMock.when(() -> SecurityContextUtil.hasAuthority(Mockito.anyString()))
				.thenReturn(Mono.just(true));
		securityContextMock.when(() -> SecurityContextUtil.hasAuthority(
				Mockito.anyString(), Mockito.<Collection<? extends GrantedAuthority>>any()))
				.thenReturn(true);
		securityContextMock.when(() -> SecurityContextUtil.hasAuthority(
				Mockito.anyString(), Mockito.<List<String>>any()))
				.thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		if (securityContextMock != null) {
			securityContextMock.close();
		}
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
					return userService.create(user);
				});

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
				);

		StepVerifier.create(lifecycle)
				.expectNext(true)
				.verifyComplete();
	}

	@Test
	void deleteUser_SoftDeletes_StatusBecomesDeleted() {

		Mono<String> result = insertTestClient("BUSTHR", "Business Three", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
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
						}));

		StepVerifier.create(result)
				.assertNext(status -> assertThat(status).isEqualTo("DELETED"))
				.verifyComplete();
	}

	@Test
	void readUser_ExistingUser_ReturnsUserDetails() {

		Mono<User> result = insertTestClient("BUSFOR", "Business Four", "BUS")
				.flatMap(clientId -> insertClientHierarchy(clientId, ULong.valueOf(1), null, null, null)
						.then(insertTestUser(clientId, "readuser", "readuser@test.com", "fincity@123"))
						.flatMap(userId -> userService.read(userId)));

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
				.flatMap(userId -> userService.checkUserExistsAcrossApps(null, "existsuser@test.com", null));

		StepVerifier.create(result)
				.assertNext(exists -> assertThat(exists).isTrue())
				.verifyComplete();
	}

	@Test
	void multiTenantIsolation_UserBelongsToCorrectClient() {

		// Create two independent business clients (BUS1 and BUS2), each managed by SYSTEM.
		// Create a user in BUS1. Then switch the security context to a BUS2 admin
		// (who does NOT manage BUS1) and attempt to read the BUS1 user. The service
		// should deny access because BUS2 has no management relationship over BUS1.

		Mono<User> isolationTest = insertTestClient("BUSSIX", "Business Six", "BUS")
				.flatMap(bus1Id -> insertClientHierarchy(bus1Id, ULong.valueOf(1), null, null, null)
						.then(insertTestUser(bus1Id, "bus1user", "bus1user@test.com", "fincity@123"))
						.flatMap(bus1UserId -> insertTestClient("BUSSEV", "Business Seven", "BUS")
								.flatMap(bus2Id -> insertClientHierarchy(bus2Id, ULong.valueOf(1), null, null, null)
										.then(insertTestUser(bus2Id, "bus2admin", "bus2admin@test.com", "fincity@123"))
										.flatMap(bus2UserId -> {
											// Switch security context to BUS2 admin
											securityContextMock.close();
											securityContextMock = Mockito.mockStatic(SecurityContextUtil.class);

											ContextAuthentication bus2Auth = TestDataFactory.createBusinessAuth(
													bus2Id, "BUSSEV",
													List.of("Authorities.User_READ", "Authorities.Logged_IN"));

											securityContextMock.when(SecurityContextUtil::getUsersContextAuthentication)
													.thenReturn(Mono.just(bus2Auth));
											securityContextMock.when(SecurityContextUtil::getUsersContextUser)
													.thenReturn(Mono.just(bus2Auth.getUser()));
											securityContextMock.when(
															() -> SecurityContextUtil.hasAuthority(Mockito.anyString()))
													.thenReturn(Mono.just(true));
											securityContextMock.when(
															() -> SecurityContextUtil.hasAuthority(
																	Mockito.anyString(),
																	Mockito.<Collection<? extends GrantedAuthority>>any()))
													.thenReturn(true);
											securityContextMock.when(
															() -> SecurityContextUtil.hasAuthority(
																	Mockito.anyString(),
																	Mockito.<List<String>>any()))
													.thenReturn(true);

											// Try to read the BUS1 user while authenticated as BUS2
											return userService.read(bus1UserId);
										}))));

		// The read should fail (empty or forbidden) because BUS2 does not manage BUS1.
		// The UserService.read() checks authority then delegates to the parent which
		// filters by accessible clients. For a non-system user without management
		// over BUS1, the result should be empty or an error.
		StepVerifier.create(isolationTest)
				.expectErrorMatches(throwable ->
						throwable.getMessage() != null && (
								throwable.getMessage().toLowerCase().contains("forbidden")
										|| throwable.getMessage().toLowerCase().contains("not found")
										|| throwable.getMessage().toLowerCase().contains("object")))
				.verify();
	}
}
