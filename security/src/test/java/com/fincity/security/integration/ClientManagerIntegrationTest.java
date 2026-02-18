package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.service.ClientManagerService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClientManagerIntegrationTest extends AbstractIntegrationTest {

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@Autowired
	private ClientManagerService clientManagerService;

	private MockedStatic<SecurityContextUtil> securityContextMock;

	@BeforeEach
	void setUp() {
		setupMockBeans();
		securityContextMock = Mockito.mockStatic(SecurityContextUtil.class);
		ContextAuthentication ca = TestDataFactory.createSystemAuth();
		securityContextMock.when(SecurityContextUtil::getUsersContextAuthentication)
				.thenReturn(Mono.just(ca));
	}

	@AfterEach
	void tearDown() {
		if (securityContextMock != null)
			securityContextMock.close();
		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	@Test
	void createClientManager_AssignsManagerToClient() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();

		ULong bus1Id = insertTestClient("BUS1", "Business Client 1", "BUS").block();
		insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();
		ULong userId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123").block();

		StepVerifier.create(
				clientManagerService.create(userId, bus1Id)
						.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();

		// Verify the mapping exists in the database
		StepVerifier.create(
				databaseClient.sql(
						"SELECT COUNT(*) AS cnt FROM security_client_manager WHERE CLIENT_ID = :clientId AND MANAGER_ID = :managerId")
						.bind("clientId", bus1Id.longValue())
						.bind("managerId", userId.longValue())
						.map(row -> row.get("cnt", Long.class))
						.one())
				.assertNext(count -> assertEquals(1L, count))
				.verifyComplete();
	}

	@Test
	void isUserClientManager_AfterAssignment_ReturnsTrue() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();

		ULong bus1Id = insertTestClient("BUS1", "Business Client 1", "BUS").block();
		insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();

		// Use the SYSTEM user (ID=1) as the manager. The system ContextAuthentication
		// has userId=1, so isUserClientManager(ca, ...) will look up user 1.
		ULong userId = ULong.valueOf(1);

		// Assign the SYSTEM user as manager of BUS1
		clientManagerService.create(userId, bus1Id)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))
				.block();

		// Verify isUserClientManager returns true
		StepVerifier.create(clientManagerService.isUserClientManager(ca, bus1Id))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void deleteClientManager_RemovesMapping() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();

		ULong bus1Id = insertTestClient("BUS1", "Business Client 1", "BUS").block();
		insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();
		ULong userId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123").block();

		// Create the manager mapping
		clientManagerService.create(userId, bus1Id)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))
				.block();

		// Delete the manager mapping
		StepVerifier.create(
				clientManagerService.delete(userId, bus1Id)
						.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();

		// Verify the mapping no longer exists in the database
		StepVerifier.create(
				databaseClient.sql(
						"SELECT COUNT(*) AS cnt FROM security_client_manager WHERE CLIENT_ID = :clientId AND MANAGER_ID = :managerId")
						.bind("clientId", bus1Id.longValue())
						.bind("managerId", userId.longValue())
						.map(row -> row.get("cnt", Long.class))
						.one())
				.assertNext(count -> assertEquals(0L, count))
				.verifyComplete();
	}

	@Test
	void getClientsOfUser_ReturnsManagedClientsPage() {

		ContextAuthentication ca = TestDataFactory.createSystemAuth();

		ULong bus1Id = insertTestClient("BUS1", "Business Client 1", "BUS").block();
		insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();

		ULong bus2Id = insertTestClient("BUS2", "Business Client 2", "BUS").block();
		insertClientHierarchy(bus2Id, SYSTEM_CLIENT_ID, null, null, null).block();

		ULong userId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123").block();

		// Assign user as manager of both clients
		clientManagerService.create(userId, bus1Id)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))
				.block();
		clientManagerService.create(userId, bus2Id)
				.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca))
				.block();

		// Verify getClientsOfUser returns both clients
		StepVerifier.create(
				clientManagerService.getClientsOfUser(userId, Pageable.ofSize(10))
						.contextWrite(ReactiveSecurityContextHolder.withAuthentication(ca)))
				.assertNext(page -> {
					assertEquals(2, page.getTotalElements());
					List<String> clientCodes = page.getContent().stream()
							.map(client -> client.getCode())
							.sorted()
							.toList();
					assertEquals(List.of("BUS1", "BUS2"), clientCodes);
				})
				.verifyComplete();
	}
}
