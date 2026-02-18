package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.security.dto.Client;
import com.fincity.security.service.ClientService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClientUserFilterIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientService clientService;

	private ContextAuthentication systemAuth;

	private ULong client1Id;
	private ULong client2Id;
	private ULong client3Id;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		systemAuth = TestDataFactory.createSystemAuth();

		// Create 3 clients with different users
		client1Id = insertTestClient("CLI1", "Client One", "BUS").block();
		client2Id = insertTestClient("CLI2", "Client Two", "BUS").block();
		client3Id = insertTestClient("CLI3", "Client Three", "BUS").block();

		// Client1 users: john (ACTIVE), jane (ACTIVE)
		insertTestUser(client1Id, "john_doe", "john@client1.com", "password123").block();
		insertTestUserWithDetails(client1Id, "jane_smith", "jane@client1.com", "Jane", "Smith", "ACTIVE",
				"+1111111111").block();

		// Client2 users: bob (ACTIVE), alice (INACTIVE)
		insertTestUser(client2Id, "bob_jones", "bob@client2.com", "password123").block();
		insertTestUserWithDetails(client2Id, "alice_wonder", "alice@client2.com", "Alice", "Wonder", "INACTIVE",
				"+2222222222").block();

		// Client3 users: charlie (LOCKED)
		insertTestUserWithDetails(client3Id, "charlie_brown", "charlie@client3.com", "Charlie", "Brown", "LOCKED",
				"+3333333333").block();
	}

	@AfterEach
	void tearDown() {
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	private Mono<ULong> insertTestUserWithDetails(ULong clientId, String userName, String email,
			String firstName, String lastName, String statusCode, String phoneNumber) {
		return databaseClient.sql(
				"INSERT INTO security_user (CLIENT_ID, USER_NAME, EMAIL_ID, FIRST_NAME, LAST_NAME, PASSWORD, PASSWORD_HASHED, STATUS_CODE, PHONE_NUMBER) "
						+ "VALUES (:clientId, :userName, :email, :firstName, :lastName, 'password', false, :statusCode, :phoneNumber)")
				.bind("clientId", clientId.longValue())
				.bind("userName", userName)
				.bind("email", email)
				.bind("firstName", firstName)
				.bind("lastName", lastName)
				.bind("statusCode", statusCode)
				.bind("phoneNumber", phoneNumber)
				.filter(s -> s.returnGeneratedValues("ID"))
				.map(row -> ULong.valueOf(row.get("ID", Long.class)))
				.one();
	}

	@Nested
	@DisplayName("user.userName filter")
	class UserNameFilterTests {

		@Test
		@DisplayName("EQUALS - should return only the client whose user matches the exact userName")
		void filterByUserName_Equals() {

			FilterCondition condition = new FilterCondition()
					.setField("user.userName")
					.setOperator(FilterConditionOperator.EQUALS)
					.setValue("john_doe");

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertEquals(1, page.getTotalElements());
						assertEquals("CLI1", page.getContent().getFirst().getCode());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("STRING_LOOSE_EQUAL - should return clients with users whose userName contains the value")
		void filterByUserName_StringLooseEqual() {

			FilterCondition condition = new FilterCondition()
					.setField("user.userName")
					.setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL)
					.setValue("_doe");

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertEquals(1, page.getTotalElements());
						assertEquals("CLI1", page.getContent().getFirst().getCode());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("user.emailId filter")
	class EmailFilterTests {

		@Test
		@DisplayName("STRING_LOOSE_EQUAL - should return clients with users whose email contains the value")
		void filterByEmail_StringLooseEqual() {

			FilterCondition condition = new FilterCondition()
					.setField("user.emailId")
					.setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL)
					.setValue("@client2.com");

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertEquals(1, page.getTotalElements());
						assertEquals("CLI2", page.getContent().getFirst().getCode());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("user.statusCode filter")
	class StatusCodeFilterTests {

		@Test
		@DisplayName("EQUALS - should return clients that have at least one user with matching status")
		void filterByStatusCode_Equals_Active() {

			FilterCondition condition = new FilterCondition()
					.setField("user.statusCode")
					.setOperator(FilterConditionOperator.EQUALS)
					.setValue("ACTIVE");

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						// Client1 has ACTIVE users, Client2 has one ACTIVE user
						// Client3 has no ACTIVE users
						assertTrue(page.getTotalElements() >= 2);
						List<String> codes = page.getContent().stream()
								.map(Client::getCode)
								.sorted()
								.toList();
						assertTrue(codes.contains("CLI1"));
						assertTrue(codes.contains("CLI2"));
						assertFalse(codes.contains("CLI3"));
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("EQUALS - should return only clients with LOCKED users")
		void filterByStatusCode_Equals_Locked() {

			FilterCondition condition = new FilterCondition()
					.setField("user.statusCode")
					.setOperator(FilterConditionOperator.EQUALS)
					.setValue("LOCKED");

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertEquals(1, page.getTotalElements());
						assertEquals("CLI3", page.getContent().getFirst().getCode());
					})
					.verifyComplete();
		}

		@Test
		@DisplayName("IN - should return clients that have users with any of the specified statuses")
		void filterByStatusCode_In() {

			FilterCondition condition = new FilterCondition()
					.setField("user.statusCode")
					.setOperator(FilterConditionOperator.IN)
					.setMultiValue(List.of("INACTIVE", "LOCKED"));

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						// Client2 has INACTIVE user, Client3 has LOCKED user
						List<String> codes = page.getContent().stream()
								.map(Client::getCode)
								.sorted()
								.toList();
						assertTrue(codes.contains("CLI2"));
						assertTrue(codes.contains("CLI3"));
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("user.firstName filter")
	class FirstNameFilterTests {

		@Test
		@DisplayName("EQUALS - should return client whose user has the matching firstName")
		void filterByFirstName_Equals() {

			FilterCondition condition = new FilterCondition()
					.setField("user.firstName")
					.setOperator(FilterConditionOperator.EQUALS)
					.setValue("Charlie");

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertEquals(1, page.getTotalElements());
						assertEquals("CLI3", page.getContent().getFirst().getCode());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("user.phoneNumber filter")
	class PhoneNumberFilterTests {

		@Test
		@DisplayName("EQUALS - should return client whose user has the matching phoneNumber")
		void filterByPhoneNumber_Equals() {

			FilterCondition condition = new FilterCondition()
					.setField("user.phoneNumber")
					.setOperator(FilterConditionOperator.EQUALS)
					.setValue("+2222222222");

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertEquals(1, page.getTotalElements());
						assertEquals("CLI2", page.getContent().getFirst().getCode());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Combined user filters with ComplexCondition")
	class CombinedFilterTests {

		@Test
		@DisplayName("AND - should return clients matching ALL user field conditions")
		void filterByMultipleUserFields_And() {

			ComplexCondition condition = new ComplexCondition()
					.setOperator(ComplexConditionOperator.AND)
					.setConditions(List.of(
							new FilterCondition()
									.setField("user.statusCode")
									.setOperator(FilterConditionOperator.EQUALS)
									.setValue("ACTIVE"),
							new FilterCondition()
									.setField("user.emailId")
									.setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL)
									.setValue("@client1.com")));

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						assertEquals(1, page.getTotalElements());
						assertEquals("CLI1", page.getContent().getFirst().getCode());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Invalid user field filter")
	class InvalidFieldTests {

		@Test
		@DisplayName("Unknown user field should return all clients (noCondition)")
		void filterByUnknownUserField_ReturnsAll() {

			FilterCondition condition = new FilterCondition()
					.setField("user.nonExistentField")
					.setOperator(FilterConditionOperator.EQUALS)
					.setValue("anything");

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						// noCondition means no filtering, so all clients should be returned
						// (at minimum the 3 test clients + the system client)
						assertTrue(page.getTotalElements() >= 3);
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("Negate user filter")
	class NegateFilterTests {

		@Test
		@DisplayName("Negated filter should exclude clients whose users match")
		void filterByUserName_Negated() {

			FilterCondition condition = (FilterCondition) new FilterCondition()
					.setField("user.userName")
					.setOperator(FilterConditionOperator.EQUALS)
					.setValue("charlie_brown")
					.setNegate(true);

			Pageable pageable = PageRequest.of(0, 10);

			StepVerifier.create(clientService.readPageFilterInternal(pageable, condition)
							.contextWrite(ReactiveSecurityContextHolder.withAuthentication(systemAuth)))
					.assertNext(page -> {
						List<String> codes = page.getContent().stream()
								.map(Client::getCode)
								.toList();
						// CLI3 should be excluded (charlie_brown is its user)
						assertFalse(codes.contains("CLI3"));
					})
					.verifyComplete();
		}
	}
}