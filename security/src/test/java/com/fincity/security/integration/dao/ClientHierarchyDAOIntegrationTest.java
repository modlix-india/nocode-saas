package com.fincity.security.integration.dao;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.security.dao.ClientHierarchyDAO;
import com.fincity.security.integration.AbstractIntegrationTest;

import reactor.test.StepVerifier;

class ClientHierarchyDAOIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientHierarchyDAO clientHierarchyDAO;

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
	@DisplayName("getClientHierarchy()")
	class GetClientHierarchyTests {

		@Test
		void systemClient_ReturnsHierarchy() {
			StepVerifier.create(clientHierarchyDAO.getClientHierarchy(SYSTEM_CLIENT_ID))
					.assertNext(hierarchy -> {
						assertNotNull(hierarchy);
						assertEquals(SYSTEM_CLIENT_ID, hierarchy.getClientId());
					})
					.verifyComplete();
		}

		@Test
		void customHierarchy_OneLevelDeep_ReturnsCorrectLevels() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();

			StepVerifier.create(clientHierarchyDAO.getClientHierarchy(bus1Id))
					.assertNext(hierarchy -> {
						assertNotNull(hierarchy);
						assertEquals(bus1Id, hierarchy.getClientId());
						assertEquals(SYSTEM_CLIENT_ID, hierarchy.getManageClientLevel0());
						assertNull(hierarchy.getManageClientLevel1());
					})
					.verifyComplete();
		}

		@Test
		void customHierarchy_TwoLevelsDeep_ReturnsCorrectLevels() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong ind1Id = insertTestClient("IND1", "Individual One", "BUS").block();

			insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();
			insertClientHierarchy(ind1Id, bus1Id, SYSTEM_CLIENT_ID, null, null).block();

			StepVerifier.create(clientHierarchyDAO.getClientHierarchy(ind1Id))
					.assertNext(hierarchy -> {
						assertNotNull(hierarchy);
						assertEquals(ind1Id, hierarchy.getClientId());
						assertEquals(bus1Id, hierarchy.getManageClientLevel0());
						assertEquals(SYSTEM_CLIENT_ID, hierarchy.getManageClientLevel1());
						assertNull(hierarchy.getManageClientLevel2());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentClient_ThrowsError() {
			StepVerifier.create(clientHierarchyDAO.getClientHierarchy(ULong.valueOf(999999)))
					.expectError()
					.verify();
		}
	}

	@Nested
	@DisplayName("getUserClientHierarchy()")
	class GetUserClientHierarchyTests {

		@Test
		void systemUser_ReturnsSystemClientHierarchy() {
			StepVerifier.create(clientHierarchyDAO.getUserClientHierarchy(ULong.valueOf(1)))
					.assertNext(hierarchy -> {
						assertNotNull(hierarchy);
						assertEquals(SYSTEM_CLIENT_ID, hierarchy.getClientId());
					})
					.verifyComplete();
		}

		@Test
		void userOfSubClient_ReturnsSubClientHierarchy() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();
			ULong userId = insertTestUser(bus1Id, "bususer1", "bususer1@test.com", "password123").block();

			StepVerifier.create(clientHierarchyDAO.getUserClientHierarchy(userId))
					.assertNext(hierarchy -> {
						assertNotNull(hierarchy);
						assertEquals(bus1Id, hierarchy.getClientId());
						assertEquals(SYSTEM_CLIENT_ID, hierarchy.getManageClientLevel0());
					})
					.verifyComplete();
		}

		@Test
		void nonExistentUser_ReturnsEmpty() {
			StepVerifier.create(clientHierarchyDAO.getUserClientHierarchy(ULong.valueOf(999999)))
					.verifyComplete();
		}
	}

	@Nested
	@DisplayName("getManagingClientIds()")
	class GetManagingClientIdsTests {

		@Test
		void systemClient_ManagesAllSubClients() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong bus2Id = insertTestClient("BUS2", "Business Two", "BUS").block();

			insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();
			insertClientHierarchy(bus2Id, SYSTEM_CLIENT_ID, null, null, null).block();

			StepVerifier.create(clientHierarchyDAO.getManagingClientIds(SYSTEM_CLIENT_ID))
					.assertNext(ids -> {
						assertNotNull(ids);
						assertTrue(ids.contains(SYSTEM_CLIENT_ID));
						assertTrue(ids.contains(bus1Id));
						assertTrue(ids.contains(bus2Id));
						assertEquals(3, ids.size());
					})
					.verifyComplete();
		}

		@Test
		void multiLevelHierarchy_ReturnsTransitiveClients() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong ind1Id = insertTestClient("IND1", "Individual One", "BUS").block();

			insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();
			insertClientHierarchy(ind1Id, bus1Id, SYSTEM_CLIENT_ID, null, null).block();

			StepVerifier.create(clientHierarchyDAO.getManagingClientIds(bus1Id))
					.assertNext(ids -> {
						assertNotNull(ids);
						assertTrue(ids.contains(bus1Id));
						assertTrue(ids.contains(ind1Id));
						assertEquals(2, ids.size());
					})
					.verifyComplete();
		}

		@Test
		void leafClient_ManagesOnlyItself() {
			ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
			ULong ind1Id = insertTestClient("IND1", "Individual One", "BUS").block();

			insertClientHierarchy(bus1Id, SYSTEM_CLIENT_ID, null, null, null).block();
			insertClientHierarchy(ind1Id, bus1Id, SYSTEM_CLIENT_ID, null, null).block();

			StepVerifier.create(clientHierarchyDAO.getManagingClientIds(ind1Id))
					.assertNext(ids -> {
						assertNotNull(ids);
						assertTrue(ids.contains(ind1Id));
						assertEquals(1, ids.size());
					})
					.verifyComplete();
		}
	}
}
