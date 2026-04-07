package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.User;
import com.fincity.security.service.UserService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.test.StepVerifier;

@DisplayName("User query with multiple client managers should not return duplicates")
class UserDuplicateManagerIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private UserService userService;

	private ULong managerClientId;
	private ULong targetClientId;
	private ULong managerUserId1;
	private ULong managerUserId2;

	@BeforeEach
	void setUp() {
		setupMockBeans();

		// Create the manager client (BUS type) that manages the target client
		managerClientId = insertTestClient("MGRCLI", "Manager Client", "BUS").block();

		// Create the target client whose users will be queried
		targetClientId = insertTestClient("TGTCLI", "Target Client", "BUS").block();

		// Create hierarchy: target client is managed by manager client
		insertClientHierarchy(targetClientId, managerClientId, null, null, null).block();

		// Create two manager users in the manager client
		managerUserId1 = insertTestUser(managerClientId, "manager1", "manager1@mgr.com", "password123").block();
		managerUserId2 = insertTestUser(managerClientId, "manager2", "manager2@mgr.com", "password123").block();

		// Assign BOTH manager users as managers of the target client
		insertClientManager(targetClientId, managerUserId1).block();
		insertClientManager(targetClientId, managerUserId2).block();

		// Create users in the target client
		insertTestUser(targetClientId, "user_alpha", "alpha@target.com", "password123").block();
		insertTestUser(targetClientId, "user_beta", "beta@target.com", "password123").block();
		insertTestUser(targetClientId, "user_gamma", "gamma@target.com", "password123").block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_activity WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	@Test
	@DisplayName("Client with multiple managers: user query should return each user exactly once")
	void queryUsers_withMultipleManagers_noDuplicates() {

		// Use a BUS owner auth for the manager client so the JOIN path is exercised
		ContextAuthentication busOwnerAuth = TestDataFactory.createBusinessAuth(
				managerUserId1, managerClientId, "MGRCLI",
				List.of("Authorities.ROLE_Owner", "Authorities.User_READ", "Authorities.Logged_IN"));

		FilterCondition condition = new FilterCondition()
				.setField("clientId")
				.setOperator(FilterConditionOperator.EQUALS)
				.setValue(targetClientId);

		Pageable pageable = PageRequest.of(0, 25);

		StepVerifier.create(userService.readPageFilterInternal(pageable, condition)
						.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busOwnerAuth)))
				.assertNext(page -> {
					List<String> userNames = page.getContent().stream()
							.map(User::getUserName)
							.toList();

					// Should have exactly 3 users, no duplicates
					assertEquals(3, page.getTotalElements(),
							"Total count should be 3 but got " + page.getTotalElements()
									+ " — likely duplicates from multiple manager JOINs");
					assertEquals(3, userNames.size());
					assertEquals(3, userNames.stream().distinct().count(),
							"Found duplicate users in results: " + userNames);

					assertTrue(userNames.contains("user_alpha"));
					assertTrue(userNames.contains("user_beta"));
					assertTrue(userNames.contains("user_gamma"));
				})
				.verifyComplete();
	}

	@Test
	@DisplayName("Client with single manager: user query should still work correctly")
	void queryUsers_withSingleManager_worksCorrectly() {

		// Remove one manager so target client has only one
		databaseClient.sql("DELETE FROM security_client_manager WHERE MANAGER_ID = :managerId")
				.bind("managerId", managerUserId2.longValue())
				.then()
				.block();

		ContextAuthentication busOwnerAuth = TestDataFactory.createBusinessAuth(
				managerUserId1, managerClientId, "MGRCLI",
				List.of("Authorities.ROLE_Owner", "Authorities.User_READ", "Authorities.Logged_IN"));

		FilterCondition condition = new FilterCondition()
				.setField("clientId")
				.setOperator(FilterConditionOperator.EQUALS)
				.setValue(targetClientId);

		Pageable pageable = PageRequest.of(0, 25);

		StepVerifier.create(userService.readPageFilterInternal(pageable, condition)
						.contextWrite(ReactiveSecurityContextHolder.withAuthentication(busOwnerAuth)))
				.assertNext(page -> {
					assertEquals(3, page.getTotalElements());
					assertEquals(3, page.getContent().size());
				})
				.verifyComplete();
	}

	@Test
	@DisplayName("System auth should not be affected by manager JOINs")
	void queryUsers_systemAuth_noDuplicates() {

		ContextAuthentication systemAuth = TestDataFactory.createSystemAuth();

		FilterCondition condition = new FilterCondition()
				.setField("clientId")
				.setOperator(FilterConditionOperator.EQUALS)
				.setValue(targetClientId);

		Pageable pageable = PageRequest.of(0, 25);

		StepVerifier.create(userService.readPageFilterInternal(pageable, condition)
						.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
				.assertNext(page -> {
					assertEquals(3, page.getTotalElements());
					assertEquals(3, page.getContent().size());
				})
				.verifyComplete();
	}
}
