package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.Set;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.fincity.security.dao.ClientManagerDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.test.StepVerifier;

class ClientManagerDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientManagerDAO clientManagerDAO;

	private static final ULong SYSTEM_CLIENT_ID = ULong.valueOf(1);

	@BeforeEach
	void setUp() {
		setupMockBeans();
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

	@Nested
	@DisplayName("createIfNotExists()")
	class CreateIfNotExistsTests {

		@Test
		void createNew_ReturnsOne() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong managerId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123")
					.block();

			StepVerifier.create(clientManagerDAO.createIfNotExists(bus1Id, managerId, ULong.valueOf(1)))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();
		}

		@Test
		void duplicateInsert_ReturnsZero() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong managerId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123")
					.block();

			clientManagerDAO.createIfNotExists(bus1Id, managerId, ULong.valueOf(1)).block();

			StepVerifier.create(clientManagerDAO.createIfNotExists(bus1Id, managerId, ULong.valueOf(1)))
					.assertNext(result -> assertEquals(0, result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("readByClientIdAndManagerId()")
	class ReadByClientIdAndManagerIdTests {

		@Test
		void existingRecord_ReturnsClientManager() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong managerId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123")
					.block();

			insertClientManager(bus1Id, managerId).block();

			StepVerifier.create(clientManagerDAO.readByClientIdAndManagerId(bus1Id, managerId))
					.assertNext(cm -> {
						assertNotNull(cm);
						assertEquals(bus1Id, cm.getClientId());
						assertEquals(managerId, cm.getManagerId());
					})
					.verifyComplete();
		}

		@Test
		void nonExistingRecord_ReturnsEmpty() {
			StepVerifier.create(
					clientManagerDAO.readByClientIdAndManagerId(ULong.valueOf(999999), ULong.valueOf(999998)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("isManagerForClient()")
	class IsManagerForClientTests {

		@Test
		void isManager_ReturnsTrue() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong managerId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123")
					.block();

			insertClientManager(bus1Id, managerId).block();

			StepVerifier.create(clientManagerDAO.isManagerForClient(managerId, bus1Id))
					.assertNext(result -> assertTrue(result))
					.verifyComplete();
		}

		@Test
		void isNotManager_ReturnsFalse() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong nonManagerId = insertTestUser(SYSTEM_CLIENT_ID, "user1", "user1@test.com", "password123")
					.block();

			StepVerifier.create(clientManagerDAO.isManagerForClient(nonManagerId, bus1Id))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}

		@Test
		void nonExistentIds_ReturnsFalse() {
			StepVerifier.create(clientManagerDAO.isManagerForClient(ULong.valueOf(999999), ULong.valueOf(999998)))
					.assertNext(result -> assertFalse(result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getClientsOfManager()")
	class GetClientsOfManagerTests {

		@Test
		void multipleClients_ReturnsPaginatedResults() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong bus2Id = insertTestClient("BUS2", "Business Two", "BUS").block();
			ULong bus3Id = insertTestClient("BUS3", "Business Three", "BUS").block();
			ULong managerId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123")
					.block();

			insertClientManager(bus1Id, managerId).block();
			insertClientManager(bus2Id, managerId).block();
			insertClientManager(bus3Id, managerId).block();

			StepVerifier.create(clientManagerDAO.getClientsOfManager(managerId, PageRequest.of(0, 2)))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(3, page.getTotalElements());
						assertEquals(2, page.getContent().size());
					})
					.verifyComplete();

			StepVerifier.create(clientManagerDAO.getClientsOfManager(managerId, PageRequest.of(1, 2)))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(3, page.getTotalElements());
						assertEquals(1, page.getContent().size());
					})
					.verifyComplete();
		}

		@Test
		void noClients_ReturnsEmptyPage() {
			ULong managerId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123")
					.block();

			StepVerifier.create(clientManagerDAO.getClientsOfManager(managerId, PageRequest.of(0, 10)))
					.assertNext(page -> {
						assertNotNull(page);
						assertEquals(0, page.getTotalElements());
						assertTrue(page.getContent().isEmpty());
					})
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("deleteByClientIdAndManagerId()")
	class DeleteByClientIdAndManagerIdTests {

		@Test
		void existingRecord_DeletesSuccessfully() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong managerId = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123")
					.block();

			insertClientManager(bus1Id, managerId).block();

			StepVerifier.create(clientManagerDAO.deleteByClientIdAndManagerId(bus1Id, managerId))
					.assertNext(result -> assertEquals(1, result))
					.verifyComplete();

			StepVerifier.create(clientManagerDAO.readByClientIdAndManagerId(bus1Id, managerId))
					.verifyComplete();
		}

		@Test
		void nonExistentRecord_ReturnsZero() {
			StepVerifier.create(
					clientManagerDAO.deleteByClientIdAndManagerId(ULong.valueOf(999999), ULong.valueOf(999998)))
					.assertNext(result -> assertEquals(0, result))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getManagerIds()")
	class GetManagerIdsTests {

		@Test
		void multipleClientsAndManagers_ReturnsGroupedManagerIds() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong bus2Id = insertTestClient("BUS2", "Business Two", "BUS").block();
			ULong manager1Id = insertTestUser(SYSTEM_CLIENT_ID, "manager1", "manager1@test.com", "password123")
					.block();
			ULong manager2Id = insertTestUser(SYSTEM_CLIENT_ID, "manager2", "manager2@test.com", "password456")
					.block();

			insertClientManager(bus1Id, manager1Id).block();
			insertClientManager(bus1Id, manager2Id).block();
			insertClientManager(bus2Id, manager2Id).block();

			StepVerifier.create(clientManagerDAO.getManagerIds(Set.of(bus1Id, bus2Id)))
					.assertNext(result -> {
						assertNotNull(result);
						assertEquals(2, result.size());

						Collection<ULong> bus1Managers = result.get(bus1Id);
						assertNotNull(bus1Managers);
						assertEquals(2, bus1Managers.size());

						Collection<ULong> bus2Managers = result.get(bus2Id);
						assertNotNull(bus2Managers);
						assertEquals(1, bus2Managers.size());
					})
					.verifyComplete();
		}

		@Test
		void clientsWithNoManagers_ReturnsEmptyMap() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();

			StepVerifier.create(clientManagerDAO.getManagerIds(Set.of(bus1Id)))
					.assertNext(result -> {
						assertNotNull(result);
						assertTrue(result.isEmpty());
					})
					.verifyComplete();
		}
	}
}
