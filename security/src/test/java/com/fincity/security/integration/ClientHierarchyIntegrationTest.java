package com.fincity.security.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.service.ClientHierarchyService;
import com.fincity.security.testutil.TestDataFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClientHierarchyIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private ClientHierarchyService clientHierarchyService;

	private MockedStatic<SecurityContextUtil> securityContextMock;

	private static final ULong SYSTEM_ID = ULong.valueOf(1);

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
		if (securityContextMock != null) {
			securityContextMock.close();
		}

		databaseClient.sql("SET FOREIGN_KEY_CHECKS = 0").then()
				.then(databaseClient.sql("DELETE FROM security_client_hierarchy WHERE CLIENT_ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client_manager WHERE ID > 0").then())
				.then(databaseClient.sql("DELETE FROM security_user WHERE ID > 1").then())
				.then(databaseClient.sql("DELETE FROM security_client WHERE ID > 1").then())
				.then(databaseClient.sql("SET FOREIGN_KEY_CHECKS = 1").then())
				.block();
	}

	@Test
	void createClientHierarchy_3Levels_VerifiesLevels() {

		// Create business client under SYSTEM
		ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
		assertNotNull(bus1Id);

		// Create hierarchy: SYSTEM -> BUS1
		StepVerifier.create(clientHierarchyService.create(SYSTEM_ID, bus1Id))
				.assertNext(hierarchy -> {
					assertEquals(bus1Id, hierarchy.getClientId());
					assertEquals(SYSTEM_ID, hierarchy.getManageClientLevel0());
					assertNull(hierarchy.getManageClientLevel1());
					assertNull(hierarchy.getManageClientLevel2());
					assertNull(hierarchy.getManageClientLevel3());
				})
				.verifyComplete();

		// Create individual client under BUS1
		ULong ind1Id = insertTestClient("IND1", "Individual One", "BUS").block();
		assertNotNull(ind1Id);

		// Create hierarchy: SYSTEM -> BUS1 -> IND1
		StepVerifier.create(clientHierarchyService.create(bus1Id, ind1Id))
				.assertNext(hierarchy -> {
					assertEquals(ind1Id, hierarchy.getClientId());
					assertEquals(bus1Id, hierarchy.getManageClientLevel0());
					assertEquals(SYSTEM_ID, hierarchy.getManageClientLevel1());
					assertNull(hierarchy.getManageClientLevel2());
					assertNull(hierarchy.getManageClientLevel3());
				})
				.verifyComplete();

		// Verify the hierarchy via getClientHierarchy
		StepVerifier.create(clientHierarchyService.getClientHierarchy(ind1Id))
				.assertNext(hierarchy -> {
					assertEquals(ind1Id, hierarchy.getClientId());
					assertEquals(bus1Id, hierarchy.getManageClientLevel0());
					assertEquals(SYSTEM_ID, hierarchy.getManageClientLevel1());
					assertNull(hierarchy.getManageClientLevel2());
					assertNull(hierarchy.getManageClientLevel3());
				})
				.verifyComplete();
	}

	@Test
	void isClientBeingManagedBy_DirectManagement_ReturnsTrue() {

		ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
		assertNotNull(bus1Id);

		// Create hierarchy: SYSTEM -> BUS1
		clientHierarchyService.create(SYSTEM_ID, bus1Id).block();

		// System directly manages BUS1
		StepVerifier.create(clientHierarchyService.isClientBeingManagedBy(SYSTEM_ID, bus1Id))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void isClientBeingManagedBy_TransitiveManagement_ReturnsTrue() {

		ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
		assertNotNull(bus1Id);

		clientHierarchyService.create(SYSTEM_ID, bus1Id).block();

		ULong ind1Id = insertTestClient("IND1", "Individual One", "BUS").block();
		assertNotNull(ind1Id);

		// Create hierarchy: SYSTEM -> BUS1 -> IND1
		clientHierarchyService.create(bus1Id, ind1Id).block();

		// System transitively manages IND1 (through BUS1)
		StepVerifier.create(clientHierarchyService.isClientBeingManagedBy(SYSTEM_ID, ind1Id))
				.assertNext(result -> assertTrue(result))
				.verifyComplete();
	}

	@Test
	void isClientBeingManagedBy_NotInHierarchy_ReturnsFalse() {

		ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
		assertNotNull(bus1Id);

		ULong bus2Id = insertTestClient("BUS2", "Business Two", "BUS").block();
		assertNotNull(bus2Id);

		// Both under SYSTEM, but not managing each other
		clientHierarchyService.create(SYSTEM_ID, bus1Id).block();
		clientHierarchyService.create(SYSTEM_ID, bus2Id).block();

		// BUS1 does not manage BUS2
		StepVerifier.create(clientHierarchyService.isClientBeingManagedBy(bus1Id, bus2Id))
				.assertNext(result -> assertFalse(result))
				.verifyComplete();
	}

	@Test
	void getClientHierarchyIdInOrder_ReturnsCorrectOrder() {

		ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
		assertNotNull(bus1Id);

		clientHierarchyService.create(SYSTEM_ID, bus1Id).block();

		ULong ind1Id = insertTestClient("IND1", "Individual One", "BUS").block();
		assertNotNull(ind1Id);

		// Create 3-level hierarchy: SYSTEM -> BUS1 -> IND1
		clientHierarchyService.create(bus1Id, ind1Id).block();

		// getClientIdsInOrder returns [clientId, level0, level1, ...]
		// For IND1: [ind1Id, bus1Id, systemId]
		StepVerifier.create(clientHierarchyService.getClientHierarchyIdInOrder(ind1Id))
				.assertNext(result -> {
					assertEquals(3, result.size());
					assertEquals(ind1Id, result.get(0));
					assertEquals(bus1Id, result.get(1));
					assertEquals(SYSTEM_ID, result.get(2));
				})
				.verifyComplete();
	}

	@Test
	void getManagingClientIds_ReturnsAllAncestors() {

		ULong bus1Id = insertTestClient("BUS1", "Business One", "BUS").block();
		assertNotNull(bus1Id);

		clientHierarchyService.create(SYSTEM_ID, bus1Id).block();

		ULong ind1Id = insertTestClient("IND1", "Individual One", "BUS").block();
		assertNotNull(ind1Id);

		// Create 3-level hierarchy: SYSTEM -> BUS1 -> IND1
		clientHierarchyService.create(bus1Id, ind1Id).block();

		// getManagingClientIds uses DAO query that finds all CLIENT_IDs
		// where the given clientId appears in any level column or as CLIENT_ID itself.
		// For SYSTEM_ID: should return SYSTEM(1), BUS1, IND1 since SYSTEM_ID appears
		// in hierarchy rows for BUS1 (level0) and IND1 (level1), and its own row.
		StepVerifier.create(clientHierarchyService.getManagingClientIds(SYSTEM_ID))
				.assertNext(result -> {
					assertNotNull(result);
					// SYSTEM appears in its own row, BUS1's level0, and IND1's level1
					assertTrue(result.contains(SYSTEM_ID));
					assertTrue(result.contains(bus1Id));
					assertTrue(result.contains(ind1Id));
					assertEquals(3, result.size());
				})
				.verifyComplete();

		// For BUS1: should return BUS1 (own row) and IND1 (where BUS1 is level0)
		StepVerifier.create(clientHierarchyService.getManagingClientIds(bus1Id))
				.assertNext(result -> {
					assertNotNull(result);
					assertTrue(result.contains(bus1Id));
					assertTrue(result.contains(ind1Id));
					assertEquals(2, result.size());
				})
				.verifyComplete();
	}
}
